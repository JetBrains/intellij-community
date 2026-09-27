package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"slices"
	"strconv"
	"strings"
	"unicode/utf8"
)

// The composer decodes its inputs with the rules of kotlinx.serialization and `ignoreUnknownKeys = false`. A key must
// match by its exact spelling, an unknown key fails, and every property without a default is required. Null is valid
// only for a nullable property. The decoding below keeps these rules, which encoding/json does not.

// jsonObject holds the members of one JSON object. A repeated key keeps its last value, as in kotlinx.serialization.
type jsonObject struct {
	typeName string
	members  map[string]json.RawMessage
}

// jsonMember is one member of an object, in the order the object states them.
type jsonMember struct {
	name  string
	value json.RawMessage
}

func readJSONFile(file string) ([]byte, error) {
	data, err := os.ReadFile(file)
	if err != nil {
		return nil, err
	}
	if !utf8.Valid(data) {
		return nil, fmt.Errorf("%s is not valid UTF-8", file)
	}
	return data, nil
}

func decodeJSONMembers(data []byte) ([]jsonMember, error) {
	decoder := json.NewDecoder(bytes.NewReader(data))
	token, err := decoder.Token()
	if err != nil {
		return nil, err
	}
	if token != json.Delim('{') {
		return nil, fmt.Errorf("Expected start of the object '{', but had '%v' instead", token)
	}
	var members []jsonMember
	for decoder.More() {
		token, err := decoder.Token()
		if err != nil {
			return nil, err
		}
		var value json.RawMessage
		if err := decoder.Decode(&value); err != nil {
			return nil, err
		}
		members = append(members, jsonMember{name: token.(string), value: value})
	}
	if _, err := decoder.Token(); err != nil {
		return nil, err
	}
	if _, err := decoder.Token(); err != io.EOF {
		return nil, fmt.Errorf("Expected EOF after parsing, but had more data")
	}
	return members, nil
}

// decodeJSONObject reads an object with the properties names. typeName names the object in an error message.
func decodeJSONObject(data []byte, typeName string, names ...string) (jsonObject, error) {
	members, err := decodeJSONMembers(data)
	if err != nil {
		return jsonObject{}, err
	}
	object := jsonObject{typeName: typeName, members: make(map[string]json.RawMessage, len(members))}
	for _, member := range members {
		if !slices.Contains(names, member.name) {
			return jsonObject{}, fmt.Errorf("Encountered an unknown key '%s' in %s", member.name, typeName)
		}
		object.members[member.name] = member.value
	}
	return object, nil
}

// value returns the raw value of a property. It fails for a missing required property and for null in a
// non-nullable property. A nil result means the default applies.
func (object jsonObject) value(name string, required, nullable bool) (json.RawMessage, error) {
	value, exists := object.members[name]
	if !exists {
		if required {
			return nil, fmt.Errorf("Field '%s' is required for type with serial name '%s', but it was missing", name, object.typeName)
		}
		return nil, nil
	}
	if bytes.Equal(value, []byte("null")) {
		if !nullable {
			return nil, fmt.Errorf("Unexpected null for the non-nullable property '%s' of %s", name, object.typeName)
		}
		return nil, nil
	}
	return value, nil
}

func (object jsonObject) string(name string) (string, error) {
	value, err := object.value(name, true, false)
	if err != nil {
		return "", err
	}
	return decodeJSONString(value, name)
}

// optionalString reads a nullable property. A nil result means null or absent.
func (object jsonObject) optionalString(name string, required bool) (*string, error) {
	value, err := object.value(name, required, true)
	if err != nil || value == nil {
		return nil, err
	}
	result, err := decodeJSONString(value, name)
	return &result, err
}

func (object jsonObject) stringList(name string, required bool) ([]string, error) {
	value, err := object.value(name, required, false)
	if err != nil || value == nil {
		return []string{}, err
	}
	var items []json.RawMessage
	if err := json.Unmarshal(value, &items); err != nil || items == nil {
		return nil, fmt.Errorf("Expected a JSON array for '%s' of %s", name, object.typeName)
	}
	result := make([]string, 0, len(items))
	for _, item := range items {
		text, err := decodeJSONString(item, name)
		if err != nil {
			return nil, err
		}
		result = append(result, text)
	}
	return result, nil
}

func (object jsonObject) objectList(name string) ([]json.RawMessage, error) {
	value, err := object.value(name, true, false)
	if err != nil {
		return nil, err
	}
	var items []json.RawMessage
	if err := json.Unmarshal(value, &items); err != nil || items == nil {
		return nil, fmt.Errorf("Expected a JSON array for '%s' of %s", name, object.typeName)
	}
	return items, nil
}

// integer reads an Int or a Long property with the given bit size. kotlinx.serialization also accepts a quoted
// number, so this does too.
func (object jsonObject) integer(name string, bits int, nullable bool) (*int64, error) {
	value, err := object.value(name, false, nullable)
	if err != nil || value == nil {
		return nil, err
	}
	literal := string(value)
	if len(literal) >= 2 && literal[0] == '"' {
		if err := json.Unmarshal(value, &literal); err != nil {
			return nil, err
		}
	}
	result, err := strconv.ParseInt(literal, 10, bits)
	if err != nil {
		return nil, fmt.Errorf("Failed to parse the numeric property '%s' of %s: %s", name, object.typeName, value)
	}
	return &result, nil
}

// boolean reads a Boolean property. kotlinx.serialization also accepts a quoted literal in any letter case, so this
// does too.
func (object jsonObject) boolean(name string) (bool, error) {
	value, err := object.value(name, false, false)
	if err != nil || value == nil {
		return false, err
	}
	literal := string(value)
	if len(literal) >= 2 && literal[0] == '"' {
		if err := json.Unmarshal(value, &literal); err != nil {
			return false, err
		}
	}
	switch strings.ToLower(literal) {
	case "true":
		return true, nil
	case "false":
		return false, nil
	}
	return false, fmt.Errorf("Expected a boolean for '%s' of %s, but had %s", name, object.typeName, value)
}

// stringMap reads a `Map<String, String>` property. A nil result means null or absent.
func (object jsonObject) stringMap(name string) (*orderedMap, error) {
	value, err := object.value(name, false, true)
	if err != nil || value == nil {
		return nil, err
	}
	members, err := decodeJSONMembers(value)
	if err != nil {
		return nil, fmt.Errorf("Expected a JSON object for '%s' of %s: %w", name, object.typeName, err)
	}
	result := &orderedMap{values: make(map[string]string, len(members))}
	for _, member := range members {
		text, err := decodeJSONString(member.value, name)
		if err != nil {
			return nil, err
		}
		result.put(member.name, text)
	}
	return result, nil
}

func decodeJSONString(value json.RawMessage, name string) (string, error) {
	if len(value) == 0 || value[0] != '"' {
		return "", fmt.Errorf("Expected a string literal for '%s', but had %s", name, value)
	}
	var result string
	err := json.Unmarshal(value, &result)
	return result, err
}

// orderedMap is a Kotlin LinkedHashMap of strings. A repeated key keeps its first position and takes the new value.
type orderedMap struct {
	keys   []string
	values map[string]string
}

func (m *orderedMap) put(key, value string) {
	if _, exists := m.values[key]; !exists {
		m.keys = append(m.keys, key)
	}
	m.values[key] = value
}

// appendJSONString writes value as kotlinx.serialization does. It escapes only the quote, the backslash, and the
// control characters, with lowercase hex digits.
func appendJSONString(output []byte, value string) []byte {
	const hex = "0123456789abcdef"
	output = append(output, '"')
	for index := 0; index < len(value); index++ {
		character := value[index]
		switch character {
		case '"', '\\':
			output = append(output, '\\', character)
		case '\t':
			output = append(output, '\\', 't')
		case '\b':
			output = append(output, '\\', 'b')
		case '\n':
			output = append(output, '\\', 'n')
		case '\r':
			output = append(output, '\\', 'r')
		case '\f':
			output = append(output, '\\', 'f')
		default:
			if character < 0x20 {
				output = append(output, '\\', 'u', '0', '0', hex[character>>4], hex[character&0xF])
			} else {
				output = append(output, character)
			}
		}
	}
	return append(output, '"')
}
