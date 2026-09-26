package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"
)

// orderedProperties keeps the first position of a key and the last value put under it, as a Kotlin LinkedHashMap does.
type orderedProperties struct {
	keys   []string
	values map[string]string
}

func newOrderedProperties() *orderedProperties {
	return &orderedProperties{values: make(map[string]string)}
}

func (properties *orderedProperties) put(key, value string) {
	if _, present := properties.values[key]; !present {
		properties.keys = append(properties.keys, key)
	}
	properties.values[key] = value
}

// parseJavaProperties reads text in the format of java.util.Properties.load: comments, line continuations, the
// `=`, `:` and whitespace separators, and the escapes.
func parseJavaProperties(text string) (*orderedProperties, error) {
	result := newOrderedProperties()
	lines := strings.Split(strings.ReplaceAll(strings.ReplaceAll(text, "\r\n", "\n"), "\r", "\n"), "\n")
	for index := 0; index < len(lines); index++ {
		line := strings.TrimLeft(lines[index], " \t\f")
		if line == "" || line[0] == '#' || line[0] == '!' {
			continue
		}
		for endsWithOddBackslashes(line) && index+1 < len(lines) {
			index++
			line = line[:len(line)-1] + strings.TrimLeft(lines[index], " \t\f")
		}
		if endsWithOddBackslashes(line) {
			line = line[:len(line)-1]
		}
		keyEnd, valueStart := len(line), len(line)
		for position := 0; position < len(line); position++ {
			char := line[position]
			if char == '\\' {
				position++
				continue
			}
			if char == '=' || char == ':' || char == ' ' || char == '\t' || char == '\f' {
				keyEnd = position
				valueStart = position + 1
				// Whitespace, then at most one `=` or `:`, then whitespace separate the key from the value.
				for valueStart < len(line) && (line[valueStart] == ' ' || line[valueStart] == '\t' || line[valueStart] == '\f') {
					valueStart++
				}
				if (char == ' ' || char == '\t' || char == '\f') && valueStart < len(line) && (line[valueStart] == '=' || line[valueStart] == ':') {
					valueStart++
					for valueStart < len(line) && (line[valueStart] == ' ' || line[valueStart] == '\t' || line[valueStart] == '\f') {
						valueStart++
					}
				}
				break
			}
		}
		key, err := unescapeJavaProperty(line[:keyEnd])
		if err != nil {
			return nil, err
		}
		value, err := unescapeJavaProperty(line[min(valueStart, len(line)):])
		if err != nil {
			return nil, err
		}
		result.put(key, value)
	}
	return result, nil
}

func endsWithOddBackslashes(line string) bool {
	count := 0
	for position := len(line) - 1; position >= 0 && line[position] == '\\'; position-- {
		count++
	}
	return count%2 == 1
}

func unescapeJavaProperty(text string) (string, error) {
	if !strings.Contains(text, "\\") {
		return text, nil
	}
	var builder strings.Builder
	for position := 0; position < len(text); position++ {
		char := text[position]
		if char != '\\' || position+1 == len(text) {
			builder.WriteByte(char)
			continue
		}
		position++
		switch text[position] {
		case 't':
			builder.WriteByte('\t')
		case 'n':
			builder.WriteByte('\n')
		case 'r':
			builder.WriteByte('\r')
		case 'f':
			builder.WriteByte('\f')
		case 'u':
			if position+5 > len(text) {
				return "", fmt.Errorf("malformed \\u escape in %q", text)
			}
			var code rune
			for _, digit := range text[position+1 : position+5] {
				var value rune
				switch {
				case digit >= '0' && digit <= '9':
					value = digit - '0'
				case digit >= 'a' && digit <= 'f':
					value = digit - 'a' + 10
				case digit >= 'A' && digit <= 'F':
					value = digit - 'A' + 10
				default:
					return "", fmt.Errorf("malformed \\u escape in %q", text)
				}
				code = code*16 + value
			}
			builder.WriteRune(code)
			position += 4
		default:
			builder.WriteByte(text[position])
		}
	}
	return builder.String(), nil
}

// productInfo holds the parts of bin/product-info.json a dev launch reads.
type productInfo struct {
	Launch []struct {
		AdditionalJvmArguments []string `json:"additionalJvmArguments"`
		CustomCommands         []struct {
			Commands               []string `json:"commands"`
			MainClass              string   `json:"mainClass"`
			AdditionalJvmArguments []string `json:"additionalJvmArguments"`
		} `json:"customCommands"`
	} `json:"launch"`
}

func readProductInfo(home string) (productInfo, error) {
	var result productInfo
	content, err := os.ReadFile(filepath.Join(home, "bin", "product-info.json"))
	if err != nil {
		return result, err
	}
	if err := json.Unmarshal(content, &result); err != nil {
		return result, fmt.Errorf("read product-info.json: %w", err)
	}
	return result, nil
}

// resolveIdeHomeMacro substitutes the IDE_HOME macro of product-info.json for the host OS, as BuildServer.kt does.
func resolveIdeHomeMacro(argument, home string) string {
	macro := "$IDE_HOME"
	switch runtime.GOOS {
	case "windows":
		macro = "%IDE_HOME%"
	case "darwin":
		macro = "$APP_PACKAGE/Contents"
	}
	return strings.ReplaceAll(argument, macro, home)
}

// putSystemProperty stores the `-D` argument [argument] in [properties], a property without `=` with an empty value.
func putSystemProperty(properties *orderedProperties, argument string) bool {
	property, isProperty := strings.CutPrefix(argument, "-D")
	if !isProperty {
		return false
	}
	key, value, _ := strings.Cut(property, "=")
	properties.put(key, value)
	return true
}

// distributionProperties are the system properties of the distribution at [home]: bin/idea.properties, the `-D`
// lines of the single bin/*.vmoptions file with `jb.vmOptionsFile`, and the `-D` arguments of the first launch of
// bin/product-info.json. This is `getIdeSystemProperties` of BuildServer.kt.
func distributionProperties(home string, info productInfo) (*orderedProperties, error) {
	text, err := os.ReadFile(filepath.Join(home, "bin", "idea.properties"))
	if err != nil {
		return nil, err
	}
	result, err := parseJavaProperties(string(text))
	if err != nil {
		return nil, err
	}
	vmOptionsFiles, err := filepath.Glob(filepath.Join(home, "bin", "*.vmoptions"))
	if err != nil {
		return nil, err
	}
	if len(vmOptionsFiles) != 1 {
		return nil, fmt.Errorf("no single *.vmoptions file in %s: %v", filepath.Join(home, "bin"), vmOptionsFiles)
	}
	vmOptions, err := readLines(vmOptionsFiles[0])
	if err != nil {
		return nil, err
	}
	for _, line := range vmOptions {
		putSystemProperty(result, line)
	}
	result.put("jb.vmOptionsFile", vmOptionsFiles[0])
	if len(info.Launch) > 0 {
		for _, argument := range info.Launch[0].AdditionalJvmArguments {
			putSystemProperty(result, resolveIdeHomeMacro(argument, home))
		}
	}
	return result, nil
}

// customCommand returns the main class and the system properties of the custom command of the distribution that
// handles [command]. This is `readCustomCommandLaunch` of BuildServer.kt.
func customCommand(home string, info productInfo, command string) (string, *orderedProperties, error) {
	if len(info.Launch) != 1 {
		return "", nil, fmt.Errorf("product-info.json of %s states %d launches, and a dev distribution has one", home, len(info.Launch))
	}
	for _, candidate := range info.Launch[0].CustomCommands {
		for _, name := range candidate.Commands {
			if name != command {
				continue
			}
			if candidate.MainClass == "" {
				return "", nil, fmt.Errorf("the custom command '%s' names no main class", command)
			}
			properties := newOrderedProperties()
			for _, argument := range candidate.AdditionalJvmArguments {
				property, isProperty := strings.CutPrefix(resolveIdeHomeMacro(argument, home), "-D")
				if !isProperty {
					continue
				}
				key, value, _ := strings.Cut(property, "=")
				if strings.Contains(value, "$") {
					return "", nil, fmt.Errorf("unsubstituted macro in JVM argument: %s", property)
				}
				properties.put(key, value)
			}
			return candidate.MainClass, properties, nil
		}
	}
	return "", nil, fmt.Errorf("no custom command found for %s", command)
}

func readLines(file string) ([]string, error) {
	input, err := os.Open(file)
	if err != nil {
		return nil, err
	}
	defer input.Close()
	var result []string
	scanner := bufio.NewScanner(input)
	scanner.Buffer(make([]byte, 0, 64*1024), 16*1024*1024)
	for scanner.Scan() {
		result = append(result, scanner.Text())
	}
	return result, scanner.Err()
}
