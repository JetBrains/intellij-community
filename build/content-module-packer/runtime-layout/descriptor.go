package main

import (
	"encoding/xml"
	"errors"
	"fmt"
	"io"
	"os"
	"strings"
)

// contentModule is one `<content><module>` element of a plugin descriptor.
type contentModule struct {
	name    string
	loading string
}

// readContentOrder returns the content modules of a plugin descriptor in the order of its `<content>` elements.
// `computeModuleSourcesByContent` walks the same elements and skips a name with a `/`, which names a descriptor, not a
// module.
func readContentOrder(file string) ([]contentModule, error) {
	input, err := os.Open(file)
	if err != nil {
		return nil, err
	}
	defer input.Close()
	decoder := xml.NewDecoder(input)
	var modules []contentModule
	depth := 0
	inContent := false
	for {
		token, err := decoder.Token()
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			return nil, fmt.Errorf("%s: %w", file, err)
		}
		switch element := token.(type) {
		case xml.StartElement:
			depth++
			switch {
			case depth == 2 && element.Name.Local == "content":
				inContent = true
			case depth == 3 && inContent && element.Name.Local == "module":
				name := attribute(element, "name")
				if name != "" && !strings.Contains(name, "/") {
					modules = append(modules, contentModule{name: name, loading: attribute(element, "loading")})
				}
			}
		case xml.EndElement:
			if depth == 2 {
				inContent = false
			}
			depth--
		}
	}
	return modules, nil
}

func attribute(element xml.StartElement, name string) string {
	for _, attr := range element.Attr {
		if attr.Name.Space == "" && attr.Name.Local == name {
			return attr.Value
		}
	}
	return ""
}
