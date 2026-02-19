package fleet.util

/**
 * Copied from com.intellij.filename.UniqueNameBuilder
 */
class UniqueNameBuilder<T>(private val myRoot: String, val separator: String) {
  private val paths = mutableMapOf<T, String>()
  private val rootNode = Node("", null)

  val size: Int
    get() = paths.size

  fun contains(file: T): Boolean {
    return paths.containsKey(file)
  }

  private class Node(val text: String, val parentNode: Node?) {
    val children = mutableMapOf<String, Node>()
    var nestedChildrenCount: Int = 0

    fun findOrAddChild(word: String): Node {
      return children.computeIfAbsentShim(word) { Node(word, this) }
    }
  }

  // Build a tree from path components starting from end
  // E.g. following try will be build from example
  //                                                                                   |<-------[/fabrique]  <-  [/idea]
  // /idea/pycharm/download/index.html                                                 |
  // /idea/fabrique/download/index.html           [RootNode] <- [/index.html] <- [/download] <- [/pycharm]  <- [/idea]
  // /idea/pycharm/documentation/index.html                              |
  //                                                                     |<------[/documentation] <- [/pycharm]  <- [/idea]
  fun addPath(key: T, path: String) {
    val path = path.removePrefix(myRoot)
    paths.put(key, path)

    var current = rootNode
    val pathComponentsIterator = PathComponentsIterator(path)

    pathComponentsIterator.forEach { next ->
      current = current.findOrAddChild(next)
    }

    var c: Node? = current
    while (c != null) {
      ++c.nestedChildrenCount
      c = c.parentNode
    }
  }

  fun getShortPath(key: T): String {
    val path = paths[key] ?: return key.toString()
    var current = rootNode
    var firstNodeWithBranches: Node? = null
    var firstNodeBeforeNodeWithBranches: Node? = null
    var fileNameNode: Node? = null

    PathComponentsIterator(path).forEach { pathComponent ->
      val parentNode = current
      current = current.findOrAddChild(pathComponent)

      if (fileNameNode == null) fileNameNode = current
      if (firstNodeBeforeNodeWithBranches == null && firstNodeWithBranches != null && current.children.size <= 1) {
        if (parentNode.nestedChildrenCount - parentNode.children.size < 1) {
          firstNodeBeforeNodeWithBranches = current
        }
      }

      if (current.children.size != 1 && firstNodeWithBranches == null) {
        firstNodeWithBranches = current
      }
    }

    val b = StringBuilder()
    if (firstNodeBeforeNodeWithBranches == null) {
      firstNodeBeforeNodeWithBranches = current
    }

    var skipFirstSeparator = true

    val nodes = generateSequence(firstNodeBeforeNodeWithBranches) { node -> node.parentNode.takeIf { it != rootNode } }
      .iterator()

    var next: Node
    while (nodes.hasNext()) {
      next = nodes.next()

      if (next != fileNameNode && next != firstNodeBeforeNodeWithBranches && next.parentNode?.children?.size == 1) {
        b.append(this.separator)
        b.append("\u2026")

        while (nodes.hasNext() && next.parentNode != fileNameNode && next.parentNode?.children?.size == 1) {
          next = nodes.next()
        }
      }
      else {
        if (next.text.startsWith(VFS_SEPARATOR)) {
          if (!skipFirstSeparator) b.append(this.separator)
          skipFirstSeparator = false
          b.append(next.text, VFS_SEPARATOR.length, next.text.length)
        }
        else {
          b.append(next.text)
        }
      }
    }

    return b.toString()
  }

  private class PathComponentsIterator(private val myPath: String) : Iterator<String> {
    private var myLastPos: Int = myPath.length
    private var mySeparatorPos: Int = myPath.lastIndexOf(VFS_SEPARATOR)

    override fun hasNext(): Boolean = myLastPos != 0

    override fun next(): String {
      if (myLastPos == 0) throw NoSuchElementException()
      var pathComponent: String

      if (mySeparatorPos != -1) {
        pathComponent = myPath.substring(mySeparatorPos, myLastPos)
        myLastPos = mySeparatorPos
        mySeparatorPos = myPath.lastIndexOf(VFS_SEPARATOR, myLastPos - 1)
      }
      else {
        pathComponent = myPath.substring(0, myLastPos)
        if (!pathComponent.startsWith(VFS_SEPARATOR)) pathComponent = VFS_SEPARATOR + pathComponent
        myLastPos = 0
      }
      return pathComponent
    }
  }

  companion object {
    private const val VFS_SEPARATOR = "/"
  }
}
