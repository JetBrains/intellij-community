package org.intellij.images.util.imageio.svg

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.imageio.stream.MemoryCacheImageInputStream

class SvgImageReaderSpiTest {
  @Test
  fun `can read svg with xml header`() {
    val svg = """
    <?xml version="1.0" encoding="UTF-8" ?>
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16"> </svg>
    """.trimIndent()

    MemoryCacheImageInputStream(svg.byteInputStream()).use { stream ->
      assertTrue(SvgImageReaderSpi().canDecodeInput(stream))
    }
  }

  @Test
  fun `cannot read svg with broken xml header`() {
    val svg = """
    <?xml version="1.0" encoding="UTF-8" // broken
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16"> </svg>
    """.trimIndent()

    MemoryCacheImageInputStream(svg.byteInputStream()).use { stream ->
      assertFalse(SvgImageReaderSpi().canDecodeInput(stream))
    }
  }

  @Test
  fun `cannot read svg with broken truncated file`() {
    val svg = """
    <?xml version="1.0" encoding="UTF-8" // broken
    """.trimIndent()

    MemoryCacheImageInputStream(svg.byteInputStream()).use { stream ->
      assertFalse(SvgImageReaderSpi().canDecodeInput(stream))
    }
  }

  @Test
  fun `can read svg with whitespace before xml header`() {
    val svg = """
                                                 
                                                 
    <?xml version="1.0" encoding="UTF-8" ?>
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16"> </svg>
    """.trimIndent()

    MemoryCacheImageInputStream(svg.byteInputStream()).use { stream ->
      assertTrue(SvgImageReaderSpi().canDecodeInput(stream))
    }
  }
  @Test
  fun `can read svg with comment after xml header`() {
    val svg = """
    <?xml version="1.0" encoding="UTF-8" ?>
    <!-- comment -->
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16"> </svg>
    """.trimIndent()

    MemoryCacheImageInputStream(svg.byteInputStream()).use { stream ->
      assertTrue(SvgImageReaderSpi().canDecodeInput(stream))
    }
  }

  @Test
  fun `can read svg with DOCTYPE after xml header`() {
    val svg = """
    <?xml version="1.0" standalone="no"?>
    <!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 20010904//EN"
      "http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd">
    <!-- comment -->
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16"> </svg>
    """.trimIndent()

    MemoryCacheImageInputStream(svg.byteInputStream()).use { stream ->
      assertTrue(SvgImageReaderSpi().canDecodeInput(stream))
    }
  }

  @Test
  fun `can read svg with comment before DOCTYPE`() {
    val svg = """
    <!-- comment -->
    <!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 20010904//EN"
      "http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd">
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16"> </svg>
    """.trimIndent()

    MemoryCacheImageInputStream(svg.byteInputStream()).use { stream ->
      assertTrue(SvgImageReaderSpi().canDecodeInput(stream))
    }
  }

  @Test
  fun `can read svg starting with DOCTYPE`() {
    val svg = """
    <!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 20010904//EN"
      "http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd">
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16"> </svg>
    """.trimIndent()

    MemoryCacheImageInputStream(svg.byteInputStream()).use { stream ->
      assertTrue(SvgImageReaderSpi().canDecodeInput(stream))
    }
  }

  @Test
  fun `can read svg starting with DOCTYPE having spaces`() {
    val svg = """
    <!DOCTYPE        svg   PUBLIC "-//W3C//DTD SVG 20010904//EN"
      "http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd">
    <!-- comment -->
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16"> </svg>
    """.trimIndent()

    MemoryCacheImageInputStream(svg.byteInputStream()).use { stream ->
      assertTrue(SvgImageReaderSpi().canDecodeInput(stream))
    }
  }

  @Test
  fun `can read svg starting with SVG root tag`() {
    val svg = """
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16"> </svg>
    """.trimIndent()

    MemoryCacheImageInputStream(svg.byteInputStream()).use { stream ->
      assertTrue(SvgImageReaderSpi().canDecodeInput(stream))
    }
  }

  @Test
  fun `can read svg starting with comment then SVG root tag`() {
    val svg = """
    <!-- comment -->
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16"> </svg>
    """.trimIndent()

    MemoryCacheImageInputStream(svg.byteInputStream()).use { stream ->
      assertTrue(SvgImageReaderSpi().canDecodeInput(stream))
    }
  }

  @Test
  fun `cannot read svg with broken comment`() {
    val svg = """
    <!-- comment -- // broken
    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16"> </svg>
    """.trimIndent()

    MemoryCacheImageInputStream(svg.byteInputStream()).use { stream ->
      assertFalse(SvgImageReaderSpi().canDecodeInput(stream))
    }
  }
  @Test
  fun `cannot read svg with broken root tag`() {
    val svg = """
    <!-- comment -->
    <sv xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16"> </svg>
    """.trimIndent()

    MemoryCacheImageInputStream(svg.byteInputStream()).use { stream ->
      assertFalse(SvgImageReaderSpi().canDecodeInput(stream))
    }
  }
}