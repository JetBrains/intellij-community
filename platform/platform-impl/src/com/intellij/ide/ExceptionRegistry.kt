/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide


import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.google.common.io.BaseEncoding
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter

// Class added for Android Studio in the com.intellij.ide package, such
// that the SystemHealthMonitor (in the same package), which tracks
// all reported exceptions and uploads them to the crash server (for users
// that have opted in) can also track frequency counts here and get them

/** Represents a stacktrace reported to the [ExceptionRegistry] */
interface StackTrace : Comparable<StackTrace> {
  /** Returns the frequency of this stack trace */
  fun count(): Int

  /** Returns the MD5 hashcode as a string */
  fun md5string(): String

  /** Produces the MD5 hash code from this stacktrace */
  fun md5(): HashCode

  /** Prints out the stack trace as a string */
  fun toStackTrace(): String

  /** Prints the stack trace to a [PrintWriter] */
  fun printStackTrace(writer: PrintWriter)

  /** Summarizes the stack trace */
  fun summarize(maxWidth: Int): String
}

/**
 * The [ExceptionRegistry] can be notified of a collection of exceptions, and it can
 * return all of these, in descending frequency order.
 *
 * # Data Structure
 *
 * The basic representation of a single throwable is to decompose it into
 * its individual stack frames, and then each frame is represented by a
 * [StackFrame] instance. Instead of using a list to hold the children of
 * a stack frame, it's using "first child" and "next sibling" references
 * instead. This avoids having to guess the number of anticipated children
 * up front when constructing array lists, or having to resize them later
 * if the guess is wrong.
 *
 * In many scenarios, particularly when something is really broken, the same
 * exception will be thrown over and over again. The [StackFrame] also has a count
 * field. This allows us to efficiently represent multiple registrations
 * of the same throwable; we match up the stack frames one by one and
 * increment their counts. This way 10,000 instances of a given throwable
 * takes the same amount of storage as a single one.
 *
 * As a small optimization, since it's quite common for there to only be
 * a single exception, this is special cased: when the first throwable comes
 * in, we simply store the full [Throwable] instance. If somebody queries
 * the top throwables at this point, we just return it. We don't actually
 * create a full tree of the individual frames. If a second throwable comes
 * in on the other hand, at that point we go and first create the tree
 * for the original throwable, and then process the new one.
 *
 * # Retrieving Exceptions
 *
 * In the above data structure, the leaf nodes uniquely identify a reported
 * exception; we can iterate back through the parent references to get the
 * exact chain of stack frames that was originally stored.
 *
 * To get the sorted exceptions, we simply gather the leaf nodes, then
 * sort them by frequency, and then we can retrieve the full stack frames
 * for each by repeatedly iterating up to the root.
 *
 * # Hashes
 *
 * The [ExceptionRegistry] is intended to be used for crash reporting, and
 * we don't currently have a simple way to report the crashes along with
 * frame frequencies. Therefore, we're planning on using fingerprints
 * for each stack frame, and then sending a simple sorted report which
 * lists the various fingerprints and their frequencies. We can then correlate
 * these on the server for the most common exceptions.
 *
 * @sample sampleUse
 */
object ExceptionRegistry {
  var count = 0
  private val root: StackFrame = StackFrame(StackTraceElement("ROOT", "", "", 0), null)
  private val leafFrames = mutableListOf<LeafFrame>()

  /** Registers an exception with the registry */
  fun register(throwable: Throwable): StackTrace {
    synchronized(this) {
      count++
      return addFrames(throwable)
    }
  }

  /** The number of reported exceptions */
  fun count(): Int = synchronized(this) { count }

  /**
   * Returns the most frequently reported exception (if there are multiple with the same highest count,
   * an arbitrary one is picked)
   */
  fun getMostFrequent(): StackTrace? {
    synchronized(this) {
      if (leafFrames.isEmpty()) {
        return null
      }
      return leafFrames.maxBy { it.count } as StackTrace
    }
  }

  /** Clears the registry */
  fun clear() {
    synchronized(this) {
      count = 0
      root.firstChild = null
      root.nextSibling = null
      leafFrames.clear()
    }
  }

  /** Returns all discovered stack traces in order of descending frequency */
  fun getStackTraces(threshold: Int = 0): List<StackTrace> {
    synchronized(this) {
      val list: MutableList<StackTrace> = leafFrames.filter { it.count() >= threshold }.toMutableList()
      list.sort()
      return list
    }
  }

  /** Given a throwable, decompose the individual frames and add them into the [StackFrame] tree structure pointed to by [root] */
  private fun addFrames(throwable: Throwable): StackTrace {
    var curr = root

    val stackTrace = throwable.stackTrace
    val max = stackTrace.size - 1
    @Suppress("LoopToCallChain")
    for (index in max downTo 1) {
      val element = stackTrace[index]
      curr = curr.addChild(element)
    }

    // Last frame: use a leaf frame instead at the end so we can store the java class name
    val leaf = curr.addLeaf(
        if (stackTrace.isNotEmpty()) stackTrace[0] else StackTraceElement(throwable.javaClass.name, "", "", 0),
        throwable.javaClass)
    if (leaf.count == 1) {
      leafFrames.add(leaf)
    }

    return leaf
  }

  /** Finds the stacktrace with the given MD5 */
  fun find(md5: String): StackTrace? {
    synchronized(this) {
      return leafFrames.firstOrNull { md5 == it.md5string() }
    }
  }

  /**
   * Represents a single stack frame (class, method, line number). For the leaf frames we use a
   * [LeafFrame] instead which carries extra data.
   */
  private open class StackFrame(val frame: StackTraceElement, val parent: StackFrame?) {
    var count = 1

    // Linked list of children: first points to first child; nextSibling *in that child* points to next sibling
    var firstChild: StackFrame? = null

    var nextSibling: StackFrame? = null

    /**
     * Adds the given stack frame as a child of this one. If already exists, bump its frequency count and return it,
     * otherwise add it as a new child.
     */
    fun addChild(frame: StackTraceElement): StackFrame {
      if (firstChild == null) {
        val child = StackFrame(frame, this)
        firstChild = child
        return child
      }
      else {
        // Try to match
        var prev: StackFrame? = null
        var curr: StackFrame? = firstChild
        while (curr != null) {
          if (curr.matches(frame)) {
            curr.count++
            return curr
          }
          prev = curr
          curr = curr.nextSibling
        }

        val child = StackFrame(frame, this)
        prev!!.nextSibling = child
        return child
      }
    }

    /** Like [addChild] but adds in a [LeafFrame] instead */
    fun addLeaf(frame: StackTraceElement, cls: Class<Any>): LeafFrame {
      if (firstChild == null) {
        val child = LeafFrame(cls, frame, this)
        firstChild = child
        return child
      }
      else {
        // Try to match
        var prev: StackFrame? = null
        var curr: StackFrame? = firstChild
        while (curr != null) {
          if (curr.matches(frame)) {
            curr.count++
            return curr as LeafFrame
          }
          prev = curr
          curr = curr.nextSibling
        }

        val child = LeafFrame(cls, frame, this)
        prev!!.nextSibling = child
        return child
      }
    }

    /** Whether the given [element] matches the stack trace element in this frame */
    private fun matches(element: StackTraceElement) = element == frame
  }

  /**
   * Class used for the leaf frames in a stack tree. This carries extra data, such as the name of the
   * class, and has methods for operating on the stack trace as a whole (e.g. summarizing it, hashing it, etc.)
   */
  private class LeafFrame(val cls: Class<Any>, frame: StackTraceElement, parent: StackFrame?) : StackFrame(frame, parent), StackTrace {
    override fun count(): Int = count

    /** Summarizes the stack trace */
    override fun summarize(maxWidth: Int): String {
      val sb = StringBuilder(maxWidth)
      var prevClass = ""
      var prevMethod = ""
      val arrow = '\u2190' // or use "<-"

      sb.append(cls.simpleName).append(": ")
      for (curr in frameSequence()) {
        with(curr.frame) {
          if (className != prevClass) {
            sb.append(className.substringAfterLast('.', ""))
          }
          if (methodName != prevMethod) {
            if (sb.last() != arrow) {
              sb.append('.')
            }
            sb.append(methodName)
          }
          if (lineNumber >= 1) {
            if (sb.last() != arrow) {
              sb.append(':')
            }
            sb.append(Integer.toString(lineNumber))
          }

          prevClass = className
          prevMethod = methodName
        }

        sb.append(arrow)

        if (sb.length > maxWidth) {
          break
        }
      }

      if (sb.endsWith(arrow)) {
        sb.trim(arrow)
      }

      if (sb.length > maxWidth) {
        sb.setLength(maxWidth - 1)
        sb.append('\u2026') // ellipsis character
      }

      return sb.toString()
    }

    /** Produces the MD5 hash code from this stacktrace */
    override fun md5(): HashCode {
      val hf = Hashing.md5() // faster than sha1 and an accidental collision wouldn't be a big deal
      val hc = hf.newHasher()

      hc.putString(cls.name, Charsets.UTF_8)

      for (curr in frameSequence()) {
        with(curr.frame) {
          hc.putString(className, Charsets.UTF_8)
          hc.putString(methodName, Charsets.UTF_8)
          hc.putString(fileName ?: "", Charsets.UTF_8)
          hc.putInt(lineNumber)
        }
      }

      return hc.hash()
    }

    private var md5String: String? = null

    /** Returns the MD5 hashcode as a string */
    override fun md5string(): String {
      if (md5String == null) {
        val hash = md5()
        val bytes = hash.asBytes()
        md5String = BaseEncoding.base16().upperCase().encode(bytes)
      }

      return md5String!!
    }

    /** Prints out the stack trace as a string */
    override fun toStackTrace(): String {
      val writer = StringWriter()
      printStackTrace(PrintWriter(writer))
      return writer.toString()
    }

    /** Prints the stack trace to a [PrintWriter] */
    override fun printStackTrace(writer: PrintWriter) {
      writer.print(cls.name)
      writer.print(":\n")
      for (curr in frameSequence()) {
        writer.print("\tat ")
        with(curr.frame) {
          writer.print(className)
          writer.print('.')
          writer.print(methodName)
          if (lineNumber == -2) {
            writer.print("(Native Method)")
          }
          else if (fileName != null) {
            writer.print('(')
            writer.print(fileName)
            writer.print(':')
            writer.print(methodName)
            writer.print(')')
          }
          writer.print("\n") // Not println to avoid \r\n on Windows
        }
      }
    }

    /** Sort by frequency, and then alphabetically by package, class, method, filename, line */
    override fun compareTo(other: StackTrace): Int {
      val otherFrame = other as LeafFrame
      var delta = otherFrame.count.compareTo(count)
      if (delta != 0) {
        return delta
      }
      delta = frame.className.compareTo(otherFrame.frame.className)
      if (delta != 0) {
        return delta
      }

      delta = frame.methodName.compareTo(otherFrame.frame.methodName)
      if (delta != 0) {
        return delta
      }

      val fileName = frame.fileName ?: ""
      val otherFileName = otherFrame.frame.fileName ?: ""
      delta = fileName.compareTo(otherFileName)
      if (delta != 0) {
        return delta
      }

      return frame.lineNumber - otherFrame.frame.lineNumber
    }

    /** Generates a sequence which iterates from the leaf up to the root (but doesn't include the root) */
    private fun frameSequence(): Sequence<StackFrame> {
      return generateSequence(this as StackFrame) { if (it.parent?.parent != null) it.parent else null }
    }
  }
}

/** Example intended for documentation but enforced by the compiler */
fun sampleUse() {
  // As exceptions happen, register them with the registry
  val exception1 = IOException()
  val exception2 = ArrayIndexOutOfBoundsException()
  for (i in 1..10) {
    ExceptionRegistry.register(exception2)
  }

  // It returns a stacktrace handle...
  val stack = ExceptionRegistry.register(exception1)

  // ...which you can use for example to get the MD5 hash of the stack
  print(stack.md5string())

  // Retrieve all exceptions with a frequency >= 10 and print out their full stack traces
  ExceptionRegistry.getStackTraces(threshold = 10).forEach {
    print(it.toStackTrace())
  }
}