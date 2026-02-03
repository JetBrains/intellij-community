data class ToolItem(
  val type: String,
  val order: Int
) {
  constructor(tool: Tool) : this(tool.type, tool.order)
}

data class Tool(
  val type: String,
  val order: Int
)

fun main() {
  listOf("1", "2", "3")
    .map { Tool(it, 1) }
    .map{ ToolItem(it) }
    .forEach {
      println(it)
    }
}