// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package stamps_test

import (
	"testing"

	"jetbrains.com/plugin-descriptor-patcher/internal/descriptorxml"
	"jetbrains.com/plugin-descriptor-patcher/internal/stamps"
)

// The curated stamps cases. Every `want` is the text `doPatchPluginXml` produced on a real classpath, over the same
// source and the same scalars.
//
// The order of the two created elements is the part a reader cannot guess: `idea-version` is created first and
// `version` second, and both insert **after the anchor**, so the second one pushes the first one along. The bytes say
// `version` precedes `idea-version` every time.
func TestTheStampsStageMatchesThePlatform(t *testing.T) {
	base := stamps.Request{
		Version:        "1.0.0",
		SinceBuild:     "263",
		UntilBuild:     "263.*",
		ReleaseDate:    "20260101",
		ReleaseVersion: "2026300",
		IsEap:          true,
	}
	retained := base
	retained.RetainProductDescriptorForBundledPlugin = true

	cases := []struct {
		name    string
		request stamps.Request
		source  string
		want    string
	}{
		{
			name:    "with no anchor both created elements go to the front",
			request: base,
			source:  "<idea-plugin><depends>x</depends></idea-plugin>",
			want: "<idea-plugin>\n" +
				"  <version>1.0.0</version>\n" +
				"  <idea-version since-build=\"263\" until-build=\"263.*\" />\n" +
				"  <depends>x</depends>\n" +
				"</idea-plugin>",
		},
		{
			name:    "id is the first anchor",
			request: base,
			source:  "<idea-plugin><id>a</id><depends>x</depends></idea-plugin>",
			want: "<idea-plugin>\n" +
				"  <id>a</id>\n" +
				"  <version>1.0.0</version>\n" +
				"  <idea-version since-build=\"263\" until-build=\"263.*\" />\n" +
				"  <depends>x</depends>\n" +
				"</idea-plugin>",
		},
		{
			name:    "name is the anchor when there is no id",
			request: base,
			source:  "<idea-plugin><name>N</name><depends>x</depends></idea-plugin>",
			want: "<idea-plugin>\n" +
				"  <name>N</name>\n" +
				"  <version>1.0.0</version>\n" +
				"  <idea-version since-build=\"263\" until-build=\"263.*\" />\n" +
				"  <depends>x</depends>\n" +
				"</idea-plugin>",
		},
		{
			name:    "id wins over name whatever the document order was",
			request: base,
			source:  "<idea-plugin><name>N</name><id>a</id></idea-plugin>",
			want: "<idea-plugin>\n" +
				"  <name>N</name>\n" +
				"  <id>a</id>\n" +
				"  <version>1.0.0</version>\n" +
				"  <idea-version since-build=\"263\" until-build=\"263.*\" />\n" +
				"</idea-plugin>",
		},
		{
			name:    "a prefixed id is not an anchor",
			request: base,
			source:  "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\"><xi:id>a</xi:id><depends>x</depends></idea-plugin>",
			want: "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n" +
				"  <version>1.0.0</version>\n" +
				"  <idea-version since-build=\"263\" until-build=\"263.*\" />\n" +
				"  <xi:id>a</xi:id>\n" +
				"  <depends>x</depends>\n" +
				"</idea-plugin>",
		},
		{
			name:    "an existing idea-version keeps its position and its other attributes",
			request: base,
			source:  "<idea-plugin><id>a</id><idea-version since-build=\"1\" other=\"k\"/></idea-plugin>",
			want: "<idea-plugin>\n" +
				"  <id>a</id>\n" +
				"  <version>1.0.0</version>\n" +
				"  <idea-version since-build=\"263\" other=\"k\" until-build=\"263.*\" />\n" +
				"</idea-plugin>",
		},
		{
			name:    "an existing version keeps its position and takes the new text",
			request: base,
			source:  "<idea-plugin><id>a</id><version>9.9</version></idea-plugin>",
			want: "<idea-plugin>\n" +
				"  <id>a</id>\n" +
				"  <idea-version since-build=\"263\" until-build=\"263.*\" />\n" +
				"  <version>1.0.0</version>\n" +
				"</idea-plugin>",
		},
		{
			name:    "a bundled plugin loses its product-descriptor",
			request: base,
			source:  "<idea-plugin><id>a</id><product-descriptor code=\"C\" release-date=\"20200101\" release-version=\"1\"/></idea-plugin>",
			want: "<idea-plugin>\n" +
				"  <id>a</id>\n" +
				"  <version>1.0.0</version>\n" +
				"  <idea-version since-build=\"263\" until-build=\"263.*\" />\n" +
				"</idea-plugin>",
		},
		{
			name:    "a retained product-descriptor keeps a stated release date and gains eap last",
			request: retained,
			source:  "<idea-plugin><id>a</id><product-descriptor code=\"C\" release-date=\"20200101\" release-version=\"1\"/></idea-plugin>",
			want: "<idea-plugin>\n" +
				"  <id>a</id>\n" +
				"  <version>1.0.0</version>\n" +
				"  <idea-version since-build=\"263\" until-build=\"263.*\" />\n" +
				"  <product-descriptor code=\"C\" release-date=\"20200101\" release-version=\"2026300\" eap=\"true\" />\n" +
				"</idea-plugin>",
		},
		{
			name:    "a release date that starts with two underscores is a placeholder and is replaced",
			request: retained,
			source:  "<idea-plugin><id>a</id><product-descriptor code=\"C\" release-date=\"__DATE__\" release-version=\"1\"/></idea-plugin>",
			want: "<idea-plugin>\n" +
				"  <id>a</id>\n" +
				"  <version>1.0.0</version>\n" +
				"  <idea-version since-build=\"263\" until-build=\"263.*\" />\n" +
				"  <product-descriptor code=\"C\" release-date=\"20260101\" release-version=\"2026300\" eap=\"true\" />\n" +
				"</idea-plugin>",
		},
		{
			name:    "a product-descriptor with no attribute but the code gains three in stamping order",
			request: retained,
			source:  "<idea-plugin><id>a</id><product-descriptor code=\"C\"/></idea-plugin>",
			want: "<idea-plugin>\n" +
				"  <id>a</id>\n" +
				"  <version>1.0.0</version>\n" +
				"  <idea-version since-build=\"263\" until-build=\"263.*\" />\n" +
				"  <product-descriptor code=\"C\" eap=\"true\" release-date=\"20260101\" release-version=\"2026300\" />\n" +
				"</idea-plugin>",
		},
		{
			name:    "an eap the descriptor states keeps its position",
			request: retained,
			source:  "<idea-plugin><id>a</id><product-descriptor code=\"C\" eap=\"false\"/></idea-plugin>",
			want: "<idea-plugin>\n" +
				"  <id>a</id>\n" +
				"  <version>1.0.0</version>\n" +
				"  <idea-version since-build=\"263\" until-build=\"263.*\" />\n" +
				"  <product-descriptor code=\"C\" eap=\"true\" release-date=\"20260101\" release-version=\"2026300\" />\n" +
				"</idea-plugin>",
		},
		{
			name:    "a description returns to a CDATA section, which un-escapes its prose",
			request: base,
			source:  "<idea-plugin><id>a</id><description>&lt;b&gt;bold&lt;/b&gt; &amp; \"quoted\"</description></idea-plugin>",
			want: "<idea-plugin>\n" +
				"  <id>a</id>\n" +
				"  <version>1.0.0</version>\n" +
				"  <idea-version since-build=\"263\" until-build=\"263.*\" />\n" +
				"  <description><![CDATA[<b>bold</b> & \"quoted\"]]></description>\n" +
				"</idea-plugin>",
		},
		{
			name:    "change-notes returns to a CDATA section too",
			request: base,
			source:  "<idea-plugin><id>a</id><change-notes>one &lt;br/&gt; two</change-notes></idea-plugin>",
			want: "<idea-plugin>\n" +
				"  <id>a</id>\n" +
				"  <version>1.0.0</version>\n" +
				"  <idea-version since-build=\"263\" until-build=\"263.*\" />\n" +
				"  <change-notes><![CDATA[one <br/> two]]></change-notes>\n" +
				"</idea-plugin>",
		},
		{
			name:    "an empty description gets no CDATA section",
			request: base,
			source:  "<idea-plugin><id>a</id><description></description></idea-plugin>",
			want: "<idea-plugin>\n" +
				"  <id>a</id>\n" +
				"  <version>1.0.0</version>\n" +
				"  <idea-version since-build=\"263\" until-build=\"263.*\" />\n" +
				"  <description />\n" +
				"</idea-plugin>",
		},
		{
			name:    "markup inside a description is destroyed by the CDATA restoration",
			request: base,
			source:  "<idea-plugin><id>a</id><description>before<b>bold</b>after</description></idea-plugin>",
			want: "<idea-plugin>\n" +
				"  <id>a</id>\n" +
				"  <version>1.0.0</version>\n" +
				"  <idea-version since-build=\"263\" until-build=\"263.*\" />\n" +
				"  <description><![CDATA[beforeafter]]></description>\n" +
				"</idea-plugin>",
		},
	}
	for _, testCase := range cases {
		t.Run(testCase.name, func(t *testing.T) {
			element, err := descriptorxml.Read(testCase.source)
			if err != nil {
				t.Fatalf("read: %v", err)
			}
			stamps.Apply(element, testCase.request)
			if got := descriptorxml.Write(element); got != testCase.want {
				t.Errorf("got:\n%s\nwant:\n%s", got, testCase.want)
			}
		})
	}
}

// A null plugin version reaches the stage as an empty string, and `Element.setText` then adds a text node the writer
// treats as insignificant. The result is `<version />`, which is what the platform writes.
func TestAnEmptyVersionWritesAnEmptyElement(t *testing.T) {
	element, err := descriptorxml.Read("<idea-plugin><id>a</id></idea-plugin>")
	if err != nil {
		t.Fatal(err)
	}
	stamps.Apply(element, stamps.Request{SinceBuild: "1", UntilBuild: "2"})
	want := "<idea-plugin>\n  <id>a</id>\n  <version />\n  <idea-version since-build=\"1\" until-build=\"2\" />\n</idea-plugin>"
	if got := descriptorxml.Write(element); got != want {
		t.Errorf("got:\n%s\nwant:\n%s", got, want)
	}
}
