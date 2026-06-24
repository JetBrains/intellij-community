package com.example.markdown

data class MarkdownBlock(val text: String, val isHeader: Boolean)

data class Section(val header: String, val blocks: List<MarkdownBlock>)

fun groupMarkdownBlocks(blocks: List<MarkdownBlock>): List<Section> {
  val sections = mutableListOf<Section>()
  var currentHeader = "Untitled"
  var currentBlocks = mutableListOf<MarkdownBlock>()
  for (block in blocks) {
    if (block.isHeader) {
      if (currentBlocks.isNotEmpty()) {
        sections.add(Section(currentHeader, currentBlocks))
        currentBlocks = mutableListOf()
      }
      currentHeader = block.text
    } else {
      currentBlocks.add(block)
    }
  }
  if (currentBlocks.isNotEmpty()) {
    sections.add(Section(currentHeader, currentBlocks))
  }
  return sections
}
