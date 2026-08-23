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
	flags.Usage = func() {
		fmt.Fprintf(out, "usage: %s [--verify-crc] --flagfile=<path>\n", flags.Name())
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
func pack(ctx context.Context, arguments []string, baseDir string, out io.Writer) int {
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

	specs, err := jarpack.ParseFlagFile(opts.flagFile, baseDir)
	if err != nil {
		fmt.Fprintf(out, "ERROR: %v\n", err)
		return 3
	}
	if err := packAll(ctx, specs, opts.verifyCRC, out); err != nil {
		fmt.Fprintf(out, "ERROR: %v\n", err)
		return 3
	}
	return 0
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
func packAll(ctx context.Context, specs []jarpack.MergeSpec, verifyCRC bool, out io.Writer) error {
	if len(specs) == 1 {
		return packOne(ctx, specs[0], verifyCRC, out)
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
			err := packOne(ctx, spec, verifyCRC, &report)
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

func packOne(ctx context.Context, spec jarpack.MergeSpec, verifyCRC bool, out io.Writer) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	spec.VerifyCRC = verifyCRC
	duplicates, err := spec.Pack()
	if len(duplicates) != 0 {
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
