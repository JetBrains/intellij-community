// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Command content-module-packer packs the `lib/` jars of a product's content modules, one jar per module, from
// already-built module and library jars.
//
// It is the Go counterpart of the Kotlin `@rules_jvm//content-module-packer` it replaced, and produces byte-identical
// archives: the recipe arrives as a flag file, nothing here reads a project model, a ProductProperties or a plugin
// descriptor, and the output layout is a pure function of the inputs.
//
// It runs either as a Bazel persistent worker or as a one-shot process, and the same code packs in both. The worker is
// not there to amortise this binary's startup - a static Go binary starts in about two milliseconds - but Bazel's own
// per-spawn cost, which at 2 524 actions each doing about a millisecond of work is most of the build; see README.md.
// The one-shot mode is what makes a failed action reproducible by copying its command line, and it is also how a whole
// tranche is packed in one process for a parity or profiling run.
package main

import (
	"context"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"runtime/pprof"
	"strings"
	"sync"

	"jetbrains.com/content-module-packer/internal/jarpack"
	"jetbrains.com/content-module-packer/internal/span"
	"jetbrains.com/content-module-packer/internal/worker"
)

func main() {
	baseDir, err := os.Getwd()
	if err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
		os.Exit(3)
	}

	if worker.IsWorkerStartup(os.Args[1:]) {
		// A worker's cwd is the exec root and stays there for the process's life, so the base directory is resolved once
		// rather than per request.
		if err := worker.Run(func(ctx context.Context, arguments []string, out io.Writer) int {
			return pack(ctx, arguments, baseDir, out)
		}); err != nil {
			fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
			os.Exit(1)
		}
		return
	}

	os.Exit(pack(context.Background(), os.Args[1:], baseDir, os.Stderr))
}

type options struct {
	flagFile   string
	verifyCRC  bool
	cpuProfile string
	traceFile  string
}

func parseOptions(arguments []string, out io.Writer) (options, error) {
	var opts options
	flags := flag.NewFlagSet(filepath.Base(os.Args[0]), flag.ContinueOnError)
	flags.SetOutput(out)
	flags.StringVar(&opts.flagFile, "flagfile", "",
		"path to the recipe: one `output=` line per jar, followed by the `module=` and `library=` lines it merges")
	flags.BoolVar(&opts.verifyCRC, "verify-crc", false,
		"recompute each entry's CRC instead of carrying the source's, and fail on a mismatch; for parity runs, not for builds")
	flags.StringVar(&opts.cpuProfile, "cpuprofile", "",
		"write a pprof CPU profile here; for a one-shot run over a whole tranche, not for a build")
	flags.StringVar(&opts.traceFile, "trace-file", "",
		"write this run's spans here as Jaeger JSON, the format the rest of the build's trace is written in; "+
			"a pure side output, read by nothing during the build. A build names it as a `trace-file=` line in the "+
			"flag file instead - see traceDestination")
	flags.Usage = func() {
		fmt.Fprintf(out, "usage: %s [--verify-crc] [--trace-file=<path>] --flagfile=<path>\n", flags.Name())
		flags.PrintDefaults()
	}
	if err := flags.Parse(arguments); err != nil {
		return opts, err
	}
	if opts.flagFile == "" {
		return opts, fmt.Errorf("expected a `--flagfile=` argument")
	}
	if args := flags.Args(); len(args) != 0 {
		return opts, fmt.Errorf("unexpected argument %q; the recipe is passed as a single --flagfile=", args[0])
	}
	return opts, nil
}

// pack runs one request - or one process, which is the same thing - and returns the exit code it should have.
//
// One span file per *request*, which is what the packing rule declares: each request is its own Bazel action with its
// own declared output, and a worker that appended to one shared file would be writing a trace of itself rather than of
// the actions. Nothing is carried between requests.
func pack(ctx context.Context, arguments []string, baseDir string, out io.Writer) (exitCode int) {
	opts, err := parseOptions(arguments, out)
	if err != nil {
		fmt.Fprintf(out, "ERROR: %v\n", err)
		return 3
	}

	if opts.cpuProfile != "" {
		stop, err := startCPUProfile(opts.cpuProfile)
		if err != nil {
			fmt.Fprintf(out, "ERROR: %v\n", err)
			return 3
		}
		defer stop()
	}

	// The recipe is read before the tracer exists, because in a worker the destination is *in* it: Bazel splits a worker
	// spawn's arguments at the param file, so a `--trace-file=` on the spawn would belong to the worker process and to
	// its WorkerKey - one process per action. So the parse is outside the root span, deliberately: it is one small file
	// read in a handful of microseconds, and the alternative is a tracer whose destination is mutated per request, which
	// is the kind of shared mutable state a worker serving thousands of requests should not have. It also means a recipe
	// that does not parse writes no span file at all - it cannot, the destination was in the part that failed.
	specs, err := jarpack.ParseFlagFile(opts.flagFile, baseDir)
	if err != nil {
		fmt.Fprintf(out, "ERROR: %v\n", err)
		return 3
	}

	// Nil when no destination was named, and a nil tracer is a no-op down to the leaves, so nothing below asks whether
	// tracing is on.
	traceFile := traceDestination(opts, specs, baseDir)
	var tracer *span.Tracer
	if traceFile != "" {
		tracer = span.NewTracer("content-module-packer")
	}
	root := tracer.Start("pack content modules", nil)
	root.SetInt("jars", int64(len(specs)))
	defer func() {
		root.End()
		if tracer == nil {
			return
		}
		if err := tracer.WriteFile(traceFile); err != nil {
			// The jars are already written and correct, but the action declared this file as an output: a request that
			// cannot produce it has to fail rather than leave Bazel looking for it. Reported to out like every other
			// failure here - never to stdout, which in a worker is the protocol.
			fmt.Fprintf(out, "ERROR: writing the span file: %v\n", err)
			if exitCode == 0 {
				exitCode = 3
			}
		}
	}()

	if err := packAll(ctx, specs, opts.verifyCRC, tracer, root, out); err != nil {
		root.Fail(err)
		fmt.Fprintf(out, "ERROR: %v\n", err)
		return 3
	}
	return 0
}

// traceDestination is where this run writes its spans, or "" for no trace at all.
//
// Two channels, because the two callers cannot share one. A build reaches this binary as a Bazel worker, where the only
// per-action bytes are the flag file, so the rule puts `trace-file=` in there. A one-shot run - a parity check, a
// whole-tranche profile - is a command line someone typed, so it keeps `--trace-file=`, which is also the flag every
// other producer of these files takes. The command line wins where both are present, which is what makes a flag file
// captured from `bazel aquery` re-runnable with the trace pointed somewhere harmless.
//
// The flag file's value is already resolved against baseDir by ParseFlagFile, and ParseFlagFile has already refused a
// second, different one, so the first group naming one names the run's.
func traceDestination(opts options, specs []jarpack.MergeSpec, baseDir string) string {
	if opts.traceFile != "" {
		if filepath.IsAbs(opts.traceFile) {
			return opts.traceFile
		}
		return filepath.Join(baseDir, opts.traceFile)
	}
	for _, spec := range specs {
		if spec.TraceFile != "" {
			return spec.TraceFile
		}
	}
	return ""
}

func startCPUProfile(path string) (func(), error) {
	file, err := os.Create(path)
	if err != nil {
		return nil, err
	}
	if err := pprof.StartCPUProfile(file); err != nil {
		file.Close()
		return nil, err
	}
	return func() {
		pprof.StopCPUProfile()
		file.Close()
	}, nil
}

// packAll packs every jar the recipe names. A Bazel action names exactly one, so the fan-out is for the whole-tranche
// parity and profiling runs, which hand over thousands in one file.
func packAll(ctx context.Context, specs []jarpack.MergeSpec, verifyCRC bool, tracer *span.Tracer, parent *span.Span, out io.Writer) error {
	if len(specs) == 1 {
		return packOne(ctx, specs[0], verifyCRC, tracer, parent, out)
	}

	limit := runtime.GOMAXPROCS(0)
	sem := make(chan struct{}, limit)
	var wg sync.WaitGroup
	var mu sync.Mutex
	var firstErr error

	for _, spec := range specs {
		wg.Add(1)
		go func(spec jarpack.MergeSpec) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()

			mu.Lock()
			stop := firstErr != nil || ctx.Err() != nil
			mu.Unlock()
			if stop {
				return
			}

			var report strings.Builder
			err := packOne(ctx, spec, verifyCRC, tracer, parent, &report)
			mu.Lock()
			defer mu.Unlock()
			if err != nil && firstErr == nil {
				firstErr = err
			}
			// Buffered and copied under the lock: a report written straight to out would interleave with another jar's.
			io.WriteString(out, report.String())
		}(spec)
	}
	wg.Wait()
	if firstErr == nil {
		return ctx.Err()
	}
	return firstErr
}

func packOne(ctx context.Context, spec jarpack.MergeSpec, verifyCRC bool, tracer *span.Tracer, parent *span.Span, out io.Writer) error {
	if err := ctx.Err(); err != nil {
		return err
	}

	jar := tracer.Start("pack jar", parent)
	defer jar.End()
	jar.SetString("jar", filepath.Base(spec.Output))
	jar.SetInt("sources", int64(len(spec.Sources)))

	spec.VerifyCRC = verifyCRC
	duplicates, err := spec.Pack()
	if err != nil {
		jar.Fail(err)
	} else if jar != nil {
		// One extra stat per jar, and only when tracing: Merge reports what it merged, not how big the result is. Not
		// on the failure path, where the size of a half-written jar means nothing.
		if info, statErr := os.Stat(spec.Output); statErr == nil {
			jar.SetInt("bytes", info.Size())
		}
	}
	if len(duplicates) != 0 {
		jar.SetInt("duplicates", int64(len(duplicates)))
		// Not a failure: two libraries merged into one jar can legitimately carry the same service file or licence stub,
		// and the first source wins. Reported so a genuine collision - two module outputs both providing a class - is
		// visible in the action log instead of silently resolving to one of them.
		shown := duplicates
		if len(shown) > 10 {
			shown = shown[:10]
		}
		fmt.Fprintf(out, "%s: %d duplicate %s, first source wins: %s\n",
			filepath.Base(spec.Output), len(duplicates), plural(len(duplicates)), strings.Join(shown, ", "))
	}
	return err
}

func plural(n int) string {
	if n == 1 {
		return "entry"
	}
	return "entries"
}
