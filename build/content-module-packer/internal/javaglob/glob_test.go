// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package javaglob

import (
	"bufio"
	"os"
	"strconv"
	"strings"
	"testing"
)

// recordedCase is one line of testdata/java-path-matcher.txt, which testdata/RecordPathMatcher.java wrote with the JDK.
type recordedCase struct {
	pattern, name, result string
}

func readRecord(t *testing.T) []recordedCase {
	t.Helper()
	file, err := os.Open("testdata/java-path-matcher.txt")
	if err != nil {
		t.Fatal(err)
	}
	defer file.Close()
	var cases []recordedCase
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := scanner.Text()
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		fields := strings.Split(line, "\t")
		if len(fields) != 3 {
			t.Fatalf("malformed record line %q", line)
		}
		pattern, err := strconv.Unquote(fields[0])
		if err != nil {
			t.Fatalf("malformed pattern in %q: %v", line, err)
		}
		name, err := strconv.Unquote(fields[1])
		if err != nil {
			t.Fatalf("malformed name in %q: %v", line, err)
		}
		cases = append(cases, recordedCase{pattern: pattern, name: name, result: fields[2]})
	}
	if err := scanner.Err(); err != nil {
		t.Fatal(err)
	}
	if len(cases) < 150 {
		t.Fatalf("the record holds %d cases; the census and the grammar need more", len(cases))
	}
	return cases
}

func TestMatchAgreesWithTheRecordedPathMatcher(t *testing.T) {
	for _, recorded := range readRecord(t) {
		t.Run(recorded.pattern+" "+recorded.name, func(t *testing.T) {
			matcher, err := Compile(recorded.pattern)
			if recorded.result == "error" {
				if err == nil {
					t.Fatalf("compiled %q, which the JDK refuses", recorded.pattern)
				}
				return
			}
			if err != nil {
				t.Fatal(err)
			}
			want, err := strconv.ParseBool(recorded.result)
			if err != nil {
				t.Fatal(err)
			}
			if got := matcher.Match(recorded.name); got != want {
				t.Fatalf("Match(%q) = %v, the JDK answers %v", recorded.name, got, want)
			}
		})
	}
}

func TestRecordCoversTheCensusAndTheCommonExcludes(t *testing.T) {
	patterns := make(map[string]bool)
	for _, recorded := range readRecord(t) {
		patterns[recorded.pattern] = true
	}
	for _, pattern := range []string{
		// Every distinct excludeFromModule pattern of the plugin layouts.
		"ngCli/**", "angular-service/**", "server/**", "language-server/**", "vue-service/**", "js/**", "javascript/**",
		"js_reporter/**", "standardDsls/**", "com/jetbrains/builtInHelp/indexer/**", "mockito-extensions/**", "rubystubs*/**", "rubysigs*/**",
		// commonModuleExcludes.
		"**/icon-robots.txt", "icon-robots.txt", ".unmodified", ".hash", "classpath.index", "module-info.class",
	} {
		if !patterns[pattern] {
			t.Errorf("the record has no case for %q", pattern)
		}
	}
}

func TestCompileKeepsThePatternAndBuildsAnAnchoredRegex(t *testing.T) {
	tests := []struct {
		pattern, regex string
	}{
		{"js/**", `^js/` + anyCharacters + `$`},
		{"*.txt", `^[^/]*\.txt$`},
		{"a?c", `^a[^/]c$`},
		{"{a,b}.txt", `^(?:(?:a)|(?:b))\.txt$`},
		{"[!abc]", `^[^/\x{61}\x{62}\x{63}]$`},
		{"[+-0]", `^[\x{2B}-\x{2E}\x{30}]$`},
		{"[a-c]", `^[\x{61}-\x{63}]$`},
		{"[^a]", `^[\x{5E}\x{61}]$`},
		{"[.-/]", `^[\x{2E}]$`},
		{"[!-0]", `^[^/\x{2D}\x{30}]$`},
		{"\\*", `^\*$`},
	}
	for _, test := range tests {
		t.Run(test.pattern, func(t *testing.T) {
			matcher, err := Compile(test.pattern)
			if err != nil {
				t.Fatal(err)
			}
			if matcher.regex.String() != test.regex {
				t.Fatalf("regex %q, want %q", matcher.regex.String(), test.regex)
			}
		})
	}
}

func TestClassRangeAcrossTheSeparatorDropsIt(t *testing.T) {
	matcher, err := Compile("a[.-/]b")
	if err != nil {
		t.Fatal(err)
	}
	if !matcher.Match("a.b") || matcher.Match("a/b") {
		t.Fatal("the separator survived the class")
	}
	matcher, err = Compile("a[!!]b")
	if err != nil {
		t.Fatal(err)
	}
	if !matcher.Match("a.b") || matcher.Match("a!b") || matcher.Match("a/b") {
		t.Fatal("a negated class matched the separator")
	}
}

func TestMatchRemovesOneTrailingSeparator(t *testing.T) {
	matcher, err := Compile("dir")
	if err != nil {
		t.Fatal(err)
	}
	if !matcher.Match("dir/") || matcher.Match("dir//") {
		t.Fatal("trailing separator handling differs from Path.of on a clean name")
	}
	root, err := Compile("/")
	if err != nil {
		t.Fatal(err)
	}
	if !root.Match("/") {
		t.Fatal("the separator alone lost its only character")
	}
}
