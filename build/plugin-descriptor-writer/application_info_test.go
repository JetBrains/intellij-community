// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

const applicationInfoTestData = "testdata/application_info"

// The expected files contain bytes captured from the Kotlin tool before its removal, without a final newline.
func TestApplicationInfoMatchesKotlin(t *testing.T) {
	for _, tt := range []struct {
		name    string
		client  string
		product string
		build   string
		options []string
		want    string
	}{
		{"defaults", "client", "product", "build", nil, "default"},
		{"remove attributes", "client", "sparse-product", "build", nil, "sparse"},
		{"empty attributes", "client", "empty-product", "build", nil, "empty"},
		{"namespace prefixes", "prefixed-client", "prefixed-product", "build",
			[]string{"--nightly", "--branch-name=feature & test"}, "prefixed"},
		{"EAP only", "client", "product", "build", []string{"--eap-override=true"}, "eap"},
		{"suffix only", "client", "product", "build", []string{"--version-suffix-override=Preview & test"}, "suffix"},
		{"both overrides", "client", "product", "build",
			[]string{"--eap-override=custom", "--version-suffix-override=Preview"}, "overrides"},
		{"empty options", "client", "product", "build",
			[]string{"--eap-override=", "--version-suffix-override=", "--branch-name="}, "default"},
		{"valueless options", "client", "product", "build",
			[]string{"--eap-override", "--version-suffix-override", "--branch-name"}, "default"},
		{"clear options", "client", "product", "build",
			[]string{"--eap-override=true", "--eap-override=", "--version-suffix-override=Preview", "--version-suffix-override="}, "default"},
		{"EAP and empty suffix", "client", "product", "build",
			[]string{"--eap-override=true", "--version-suffix-override="}, "eap"},
		{"empty EAP and suffix", "client", "product", "build",
			[]string{"--eap-override=", "--version-suffix-override=Preview & test"}, "suffix"},
		{"long build branch", "client", "product", "build", []string{"--branch-name=ignored"}, "default"},
		{"nightly branch", "client", "product", "build",
			[]string{"--nightly", "--branch-name=feature & test"}, "nightly"},
		{"nightly without branch", "client", "product", "build", []string{"--nightly"}, "default"},
		{"nightly empty branch", "client", "product", "build",
			[]string{"--nightly=", "--nightly", "--branch-name="}, "default"},
		{"short build branch", "client", "product", "short-build", []string{"--branch-name=feature & test"}, "short"},
		{"undotted build branch", "client", "product", "undotted-build", []string{"--branch-name=feature & test"}, "undotted"},
	} {
		for _, flagfile := range []bool{false, true} {
			name := tt.name + "/direct"
			if flagfile {
				name = tt.name + "/flagfile"
			}
			t.Run(name, func(t *testing.T) {
				dir := t.TempDir()
				output := filepath.Join(dir, "out", "application-info.xml")
				arguments := []string{
					"--application-info", "--out=" + output,
					"--client-application-info=" + filepath.Join(applicationInfoTestData, tt.client+".xml"),
					"--product-application-info=" + filepath.Join(applicationInfoTestData, tt.product+".xml"),
					"--build-number=" + filepath.Join(applicationInfoTestData, tt.build+".txt"),
				}
				arguments = append(arguments, tt.options...)
				if flagfile {
					path := requestFile(t, dir, arguments...)
					write(t, path, strings.ReplaceAll(read(t, path), "\n", "\r\n")+"\r\n")
					arguments = []string{"--flagfile=" + path}
				}
				if code := run(arguments); code != 0 {
					t.Fatalf("exit %d", code)
				}
				if got, expected := read(t, output), read(t, filepath.Join(applicationInfoTestData, tt.want+".expected.xml")); got != expected {
					t.Errorf("got:\n%s\nwant:\n%s", got, expected)
				}
			})
		}
	}
}

func TestApplicationInfoRequest(t *testing.T) {
	parsed, err := parseApplicationInfoRequest([]string{
		"--out=unused", "--out=out/client.xml", "--application-info", "",
		"--client-application-info=a file=1.xml", "--product-application-info=product.xml", "--build-number=build.txt",
		"--eap-override=false", "--eap-override=true", "--version-suffix-override=Preview = 1", "--nightly=", "--nightly",
		"--branch-name=feature = test",
	})
	if err != nil {
		t.Fatal(err)
	}
	want := applicationInfoRequest{
		output:                 "out/client.xml",
		clientApplicationInfo:  "a file=1.xml",
		productApplicationInfo: "product.xml",
		buildNumber:            "build.txt",
		eapOverride:            "true",
		versionSuffixOverride:  "Preview = 1",
		nightly:                true,
		branchName:             "feature = test",
	}
	if parsed != want {
		t.Errorf("got %#v, want %#v", parsed, want)
	}
}

func TestApplicationInfoRequiresFiles(t *testing.T) {
	for _, option := range []string{"--out", "--client-application-info", "--product-application-info", "--build-number"} {
		for _, value := range []string{"missing", "empty", "valueless"} {
			t.Run(option+"/"+value, func(t *testing.T) {
				arguments := []string{"--application-info"}
				for _, required := range []string{"--out", "--client-application-info", "--product-application-info", "--build-number"} {
					if required != option {
						arguments = append(arguments, required+"=unused")
					} else if value == "empty" {
						arguments = append(arguments, required+"=")
					} else if value == "valueless" {
						arguments = append(arguments, required)
					}
				}
				if _, err := parseApplicationInfoRequest(arguments); err == nil || !strings.Contains(err.Error(), option+" is required") {
					t.Fatalf("got %v, want %s is required", err, option)
				}
				if code := run(arguments); code != 2 {
					t.Errorf("exit %d, want 2", code)
				}
			})
		}
	}
}

func TestApplicationInfoRejectsInvalidRequests(t *testing.T) {
	for _, tt := range []struct {
		arguments []string
		want      string
	}{
		{nil, "--application-info is required"},
		{[]string{"--embedded-product"}, "--application-info is required"},
		{[]string{"--application-info", "--application-info"}, "only one mode flag"},
		{[]string{"--application-info", "--embedded-product"}, "only one mode flag"},
		{[]string{"--application-info=true"}, "takes no value"},
		{[]string{"--application-info", "--nightly=true"}, "--nightly is a flag"},
		{[]string{"--application-info", "--nightly=false"}, "--nightly is a flag"},
		{[]string{"--application-info", "--unknown=1"}, "unknown frontend application info option"},
		{[]string{"--application-info", "--source=client.xml"}, "unknown frontend application info option"},
		{[]string{"--application-info", "--build-number-file=build.txt"}, "unknown frontend application info option"},
	} {
		t.Run(strings.Join(tt.arguments, " "), func(t *testing.T) {
			if _, err := parseApplicationInfoRequest(tt.arguments); err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Errorf("got %v, want %q", err, tt.want)
			}
		})
	}
}

func TestApplicationInfoRejectsInvalidInputs(t *testing.T) {
	const valid = `<component xmlns="http://jetbrains.org/intellij/schema/application-info">` +
		`<names fullname="Product" /><version /><build /></component>`
	type invalidInput struct {
		name    string
		input   string
		content string
		missing bool
		want    string
	}
	cases := []invalidInput{
		{"missing client", "client", "", true, "client.xml"},
		{"missing product", "product", "", true, "product.xml"},
		{"missing build number", "build", "", true, "build.txt"},
		{"empty build number", "build", "", false, "build number is empty"},
		{"blank build number", "build", " \t\r\n", false, "build number is empty"},
		{"no product name", "product", strings.ReplaceAll(valid, ` fullname="Product"`, ""), false, "no product name"},
		{"qualified product name", "product",
			strings.ReplaceAll(valid, `fullname="Product"`, `xmlns:p="urn:other" p:fullname="Product"`), false, "no product name"},
	}
	for _, input := range []string{"client", "product"} {
		invalid := map[string]string{
			"unclosed":        strings.TrimSuffix(valid, "</component>"),
			"mismatched":      strings.ReplaceAll(valid, "</component>", "</invalid>"),
			"second root":     valid + valid,
			"empty":           "",
			"no namespace":    strings.ReplaceAll(valid, ` xmlns="http://jetbrains.org/intellij/schema/application-info"`, ""),
			"wrong namespace": strings.ReplaceAll(valid, applicationInfoNamespace, "urn:other"),
		}
		for _, child := range []struct{ name, content string }{
			{"names", `<names fullname="Product" />`},
			{"version", `<version />`},
			{"build", `<build />`},
		} {
			invalid["missing "+child.name] = strings.ReplaceAll(valid, child.content, "")
			invalid["duplicate "+child.name] = strings.ReplaceAll(valid, child.content, child.content+child.content)
			invalid["nested "+child.name] = strings.ReplaceAll(valid, child.content, "<wrapper>"+child.content+"</wrapper>")
			invalid["unqualified "+child.name] = strings.ReplaceAll(valid, child.content, "<"+child.name+` xmlns="" />`)
			invalid["duplicate prefixed "+child.name] = strings.ReplaceAll(valid, child.content,
				child.content+"<app:"+child.name+` xmlns:app="`+applicationInfoNamespace+`" />`)
		}
		for name, content := range invalid {
			cases = append(cases, invalidInput{name + "/" + input, input, content, false, input + ".xml"})
		}
	}
	for _, tt := range cases {
		t.Run(tt.name, func(t *testing.T) {
			dir := t.TempDir()
			files := map[string]string{
				"client":  filepath.Join(dir, "client.xml"),
				"product": filepath.Join(dir, "product.xml"),
				"build":   filepath.Join(dir, "build.txt"),
			}
			for input, content := range map[string]string{"client": valid, "product": valid, "build": "263.123.4"} {
				if input == tt.input {
					if tt.missing {
						continue
					}
					content = tt.content
				}
				write(t, files[input], content)
			}
			output := filepath.Join(dir, "out.xml")
			arguments := []string{
				"--application-info", "--out=" + output, "--client-application-info=" + files["client"],
				"--product-application-info=" + files["product"], "--build-number=" + files["build"],
			}
			parsed, err := parseApplicationInfoRequest(arguments)
			if err != nil {
				t.Fatal(err)
			}
			if _, err := resolveApplicationInfo(parsed); err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Errorf("got %v, want %q", err, tt.want)
			}
			if code := run(arguments); code != 1 {
				t.Errorf("exit %d, want 1", code)
			}
			if _, err := os.Stat(output); !os.IsNotExist(err) {
				t.Errorf("the failure wrote %s", output)
			}
		})
	}
}

func TestApplicationInfoOutputFailure(t *testing.T) {
	dir := t.TempDir()
	file := filepath.Join(dir, "file")
	write(t, file, "not a directory")
	if code := run([]string{
		"--application-info", "--out=" + filepath.Join(file, "out.xml"),
		"--client-application-info=" + filepath.Join(applicationInfoTestData, "client.xml"),
		"--product-application-info=" + filepath.Join(applicationInfoTestData, "product.xml"),
		"--build-number=" + filepath.Join(applicationInfoTestData, "build.txt"),
	}); code != 1 {
		t.Errorf("exit %d, want 1", code)
	}
}
