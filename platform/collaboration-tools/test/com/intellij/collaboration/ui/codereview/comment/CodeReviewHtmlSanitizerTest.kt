// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.comment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CodeReviewHtmlSanitizerTest {
  @Test
  fun `css background image is removed`() {
    val html = """<div style="background-image:url(file:////10.0.0.1/S/a.png)">badge</div>"""
    assertThat(CodeReviewHtmlSanitizer.sanitize(html)).isEqualTo("<div>badge</div>")
  }

  @Test
  fun `css list style image is removed`() {
    val html = """<ul style="list-style-image: URL('file:////h/a.png')"><li>x</li></ul>"""
    assertThat(CodeReviewHtmlSanitizer.sanitize(html)).isEqualTo("<ul><li>x</li></ul>")
  }

  @Test
  fun `css escapes comments and entities do not hide a url`() {
    assertThat(CodeReviewHtmlSanitizer.sanitize("""<div style="background:u\rl(file:////h/a.png)">x</div>""")).isEqualTo("<div>x</div>")
    assertThat(CodeReviewHtmlSanitizer.sanitize("""<div style="background:u/**/rl(x)">x</div>""")).isEqualTo("<div>x</div>")
    assertThat(CodeReviewHtmlSanitizer.sanitize("""<div style="background:url&#40;x&#41;">x</div>""")).isEqualTo("<div>x</div>")
  }

  @Test
  fun `the first of duplicate style attributes does not hide the second one`() {
    val html = """<div style="color:red" style="background-image:url(file:////h/a.png)">x</div>"""
    assertThat(CodeReviewHtmlSanitizer.sanitize(html)).isEqualTo("""<div style="color:red">x</div>""")
  }

  @Test
  fun `resource loading elements are removed`() {
    val html = """
      <link rel="stylesheet" href="file:////h/a.css">
      <style>@import url(file:////h/b.css);</style>
      <object classid="javax.swing.JEditorPane"><param name="page" value="file:////h/c"></object>
      <iframe src="file:////h/d"></iframe>
      <input type="image" src="file:////h/e.png">
      <input type=" IMAGE " src="file:////h/f.png">
      <isindex action="file:////h/g"></isindex>
      <p>text</p>
    """.trimIndent()
    assertThat(CodeReviewHtmlSanitizer.sanitize(html).trim()).isEqualTo("<p>text</p>")
  }

  @Test
  fun `form controls that send the form are removed`() {
    val html = """<form action="file:////h/a"><input type="text"><input type="password"><input type="submit"></form>"""
    assertThat(CodeReviewHtmlSanitizer.sanitize(html)).isEqualTo("""<form action="file:////h/a"></form>""")
  }

  @Test
  fun `background attribute is removed`() {
    assertThat(CodeReviewHtmlSanitizer.sanitize("""<table background="file:////h/a.png"><tr><td background="x">c</td></tr></table>"""))
      .isEqualTo("<table><tbody><tr><td>c</td></tr></tbody></table>")
  }

  @Test
  fun `body attributes are checked`() {
    assertThat(CodeReviewHtmlSanitizer.sanitize("""<body><p>a</p><body background="file:////h/a.png" style="background:url(x)"></body>"""))
      .isEqualTo("<body><p>a</p></body>")
  }

  @Test
  fun `image with a local scheme is removed`() {
    assertThat(CodeReviewHtmlSanitizer.sanitize("""<p>a<img src="file:////h/a.png">b</p>""")).isEqualTo("<p>ab</p>")
    assertThat(CodeReviewHtmlSanitizer.sanitize("""<p>a<img src="&#1;FILE:////h/a.png">b</p>""")).isEqualTo("<p>ab</p>")
    assertThat(CodeReviewHtmlSanitizer.sanitize("""<p>a<img src="jar:file:////h/a.jar!/a.png">b</p>""")).isEqualTo("<p>ab</p>")
    assertThat(CodeReviewHtmlSanitizer.sanitize("""<p>a<img src="\\h\s\a.png">b</p>""")).isEqualTo("<p>ab</p>")
  }

  @Test
  fun `link with an unsafe target keeps only its text`() {
    assertThat(CodeReviewHtmlSanitizer.sanitize("""<p><a href="file:////h/s/a.exe">a</a></p>""")).isEqualTo("<p>a</p>")
    assertThat(CodeReviewHtmlSanitizer.sanitize("""<p><a href="&#9;FILE://h/s/a.exe"><b>a</b></a></p>""")).isEqualTo("<p><b>a</b></p>")
    assertThat(CodeReviewHtmlSanitizer.sanitize("""<p><a href="smb://h/s">a</a></p>""")).isEqualTo("<p>a</p>")
    assertThat(CodeReviewHtmlSanitizer.sanitize("""<p><a href="C:\Windows\a.exe">a</a></p>""")).isEqualTo("<p>a</p>")
    assertThat(CodeReviewHtmlSanitizer.sanitize("""<p><a href="\\h\s\a.exe">a</a></p>""")).isEqualTo("<p>a</p>")
    assertThat(CodeReviewHtmlSanitizer.sanitize("""<p><a href="/\h\s\a.exe">a</a></p>""")).isEqualTo("<p>a</p>")
    assertThat(CodeReviewHtmlSanitizer.sanitize("""<p><a href="//h/s/a.exe">a</a></p>""")).isEqualTo("<p>a</p>")
  }

  @Test
  fun `link with a web, mail or relative target stays`() {
    val html = """<p><a href="https://host/a">a</a> <a href="mailto:a@host">b</a> <a href="/group/project/-/issues/1">c</a></p>"""
    assertThat(CodeReviewHtmlSanitizer.sanitize(html)).isEqualTo(html)
  }

  @Test
  fun `link with a custom scheme stays only when the caller handles the scheme`() {
    val html = """<p><a href="glmergerequest:12">!12</a></p>"""
    assertThat(CodeReviewHtmlSanitizer.sanitize(html, setOf("glmergerequest"))).isEqualTo(html)
    assertThat(CodeReviewHtmlSanitizer.sanitize(html, setOf("GLMergeRequest"))).isEqualTo(html)
    assertThat(CodeReviewHtmlSanitizer.sanitize(html)).isEqualTo("<p>!12</p>")
  }

  @Test
  fun `link with a custom scheme and a network path keeps only its text`() {
    val schemes = setOf("glfilelink")
    assertThat(CodeReviewHtmlSanitizer.sanitize("""<p><a href="glfilelink://h/s/a.txt">a</a></p>""", schemes)).isEqualTo("<p>a</p>")
    assertThat(CodeReviewHtmlSanitizer.sanitize("""<p><a href="glfilelink:\\h\s\a.txt">a</a></p>""", schemes)).isEqualTo("<p>a</p>")
    assertThat(CodeReviewHtmlSanitizer.sanitize("""<p><a href="GLFILELINK:/\h\s\a.txt">a</a></p>""", schemes)).isEqualTo("<p>a</p>")
    val localFileLink = """<p><a href="glfilelink:/repo/a.txt">a</a></p>"""
    assertThat(CodeReviewHtmlSanitizer.sanitize(localFileLink, schemes)).isEqualTo(localFileLink)
  }

  @Test
  fun `image with a web or relative source stays`() {
    val html = """<p><img src="https://host/a.png"><img src="/uploads/b.png"><img src="data:image/png;base64,AAAA"></p>"""
    assertThat(CodeReviewHtmlSanitizer.sanitize(html)).isEqualTo(html)
  }

  @Test
  fun `code highlighting styles and task list checkboxes stay`() {
    val html = """<body><pre><code><span style="color:#0033b3;">fun</span> main</code></pre>""" +
               """<ul><li><input type="checkbox" checked disabled> done</li></ul></body>"""
    assertThat(CodeReviewHtmlSanitizer.sanitize(html)).isEqualTo(html)
  }

  @Test
  fun `blank text stays`() {
    assertThat(CodeReviewHtmlSanitizer.sanitize("")).isEqualTo("")
    assertThat(CodeReviewHtmlSanitizer.sanitize("  ")).isEqualTo("  ")
  }
}
