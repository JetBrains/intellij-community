// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Package javaglob matches an entry name against a java.nio "glob:" pattern.
//
// Compile ports sun.nio.fs.Globs.toUnixRegexPattern, so the grammar is the JDK grammar. `*` and `?` stay inside
// one name component. `**` crosses `/`. `[..]` is a class with `!` negation and ranges. `{a,b}` is a group. `\`
// escapes the next character. A match is case-sensitive and covers the whole name, as PathMatcher.matches does.
// The port refuses every pattern the JDK refuses, so a pattern never matches differently in silence.
package javaglob

import (
	"fmt"
	"regexp"
	"strings"
)

// Matcher is one compiled pattern.
type Matcher struct {
	regex *regexp.Regexp
}

// anyCharacters ports the `.*` the JDK emits for `**`. A JDK `.` matches no line terminator.
const anyCharacters = `[^\n\r\x{85}\x{2028}\x{2029}]*`

// end marks the end of the pattern inside the class parser.
const end rune = -1

// Compile translates a glob pattern into a regular expression and compiles it.
// It refuses an escape at the end, a nested or unclosed group, an unclosed or empty class, a `/` inside a class,
// and a reversed range. Each is an error in the JDK too.
func Compile(pattern string) (Matcher, error) {
	regex, err := toRegex(pattern)
	if err != nil {
		return Matcher{}, fmt.Errorf("glob %q: %w", pattern, err)
	}
	compiled, err := regexp.Compile(regex)
	if err != nil {
		return Matcher{}, fmt.Errorf("glob %q: %w", pattern, err)
	}
	return Matcher{regex: compiled}, nil
}

// Match reports whether the whole name matches.
// One trailing `/` is removed first, because Path.of removes it before the JDK matches a name.
func (matcher Matcher) Match(name string) bool {
	if len(name) > 1 && strings.HasSuffix(name, "/") {
		name = name[:len(name)-1]
	}
	return matcher.regex.MatchString(name)
}

func toRegex(glob string) (string, error) {
	runes := []rune(glob)
	var regex strings.Builder
	regex.WriteString("^")
	inGroup := false
	for i := 0; i < len(runes); {
		c := runes[i]
		i++
		switch c {
		case '\\':
			if i == len(runes) {
				return "", fmt.Errorf("no character to escape at %d", i-1)
			}
			regex.WriteString(regexp.QuoteMeta(string(runes[i])))
			i++
		case '/':
			regex.WriteRune('/')
		case '[':
			class, next, err := parseClass(runes, i)
			if err != nil {
				return "", err
			}
			regex.WriteString(class)
			i = next
		case '{':
			if inGroup {
				return "", fmt.Errorf("cannot nest groups at %d", i-1)
			}
			regex.WriteString("(?:(?:")
			inGroup = true
		case '}':
			if inGroup {
				regex.WriteString("))")
				inGroup = false
			} else {
				regex.WriteString(`\}`)
			}
		case ',':
			if inGroup {
				regex.WriteString(")|(?:")
			} else {
				regex.WriteRune(',')
			}
		case '*':
			if i < len(runes) && runes[i] == '*' {
				regex.WriteString(anyCharacters)
				i++
			} else {
				regex.WriteString("[^/]*")
			}
		case '?':
			regex.WriteString("[^/]")
		default:
			regex.WriteString(regexp.QuoteMeta(string(c)))
		}
	}
	if inGroup {
		return "", fmt.Errorf("missing '}'")
	}
	regex.WriteString("$")
	return regex.String(), nil
}

// classRange is one member of a class: a single character when low equals high.
type classRange struct {
	low, high rune
}

// parseClass reads a class after its `[` and returns the regular expression and the index after the `]`.
// The JDK emits `[[^/]&&[...]]`. RE2 has no intersection, so the `/` is removed from the members instead.
func parseClass(runes []rune, i int) (string, int, error) {
	peek := func() rune {
		if i < len(runes) {
			return runes[i]
		}
		return end
	}
	negated := false
	var members []classRange
	if peek() == '^' {
		members = append(members, classRange{'^', '^'})
		i++
	} else {
		if peek() == '!' {
			negated = true
			i++
		}
		if peek() == '-' {
			members = append(members, classRange{'-', '-'})
			i++
		}
	}
	hasRangeStart := false
	c := '['
	for i < len(runes) {
		c = runes[i]
		i++
		if c == ']' {
			break
		}
		if c == '/' {
			return "", 0, fmt.Errorf("explicit name separator in class at %d", i-1)
		}
		if c != '-' {
			members = append(members, classRange{c, c})
			hasRangeStart = true
			continue
		}
		if !hasRangeStart {
			return "", 0, fmt.Errorf("invalid range at %d", i-1)
		}
		high := peek()
		i++
		if high == end || high == ']' {
			members = append(members, classRange{'-', '-'})
			c = high
			break
		}
		if high < members[len(members)-1].low {
			return "", 0, fmt.Errorf("invalid range at %d", i-3)
		}
		members[len(members)-1].high = high
		hasRangeStart = false
	}
	if c != ']' {
		return "", 0, fmt.Errorf("missing ']'")
	}
	if len(members) == 0 {
		return "", 0, fmt.Errorf("empty class")
	}
	var class strings.Builder
	class.WriteString("[")
	if negated {
		class.WriteString("^/")
	}
	// A member never starts at `/`, because the loop refuses it, so every member keeps at least one character.
	for _, member := range members {
		if negated || member.high < '/' || member.low > '/' {
			writeMember(&class, member)
			continue
		}
		writeMember(&class, classRange{member.low, '/' - 1})
		if member.high > '/' {
			writeMember(&class, classRange{'/' + 1, member.high})
		}
	}
	class.WriteString("]")
	return class.String(), i, nil
}

func writeMember(class *strings.Builder, member classRange) {
	fmt.Fprintf(class, `\x{%X}`, member.low)
	if member.high != member.low {
		fmt.Fprintf(class, `-\x{%X}`, member.high)
	}
}
