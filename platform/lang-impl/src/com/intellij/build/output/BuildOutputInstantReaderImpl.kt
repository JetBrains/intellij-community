// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.output

import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Ref
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.Closeable
import java.util.*
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author Vladislav.Soroka
 */
class BuildOutputInstantReaderImpl(private val myBuildId: Any,
                                   buildProgressListener: BuildProgressListener,
                                   parsers: List<BuildOutputParser>) : BuildOutputInstantReader, Closeable, Appendable {
  private var myBuffer: StringBuilder? = null
  private val myQueue = LinkedTransferQueue<String>()

  private val myLinesBuffer = LinkedList<String>()
  private var myCurrentIndex = -1

  private val myThread: Thread
  private val myStarted = AtomicBoolean()
  private val myClosed = AtomicBoolean()

  init {
    myThread = Thread({
                        val lastMessageRef = Ref.create<BuildEvent>()
                        val messageConsumer = { event: BuildEvent ->
                          //do not add duplicates, e.g. sometimes same messages can be added both to stdout and stderr
                          if (event != lastMessageRef.get()) {
                            buildProgressListener.onEvent(event)
                          }
                          lastMessageRef.set(event)
                        }

                        while (true) {
                          val line = this.readLine() ?: break
                          if (line.isBlank()) continue

                          for (parser in parsers) {
                            if (parser.parse(line, BuildOutputInstantReaderWrapper(this), messageConsumer)) {
                              break
                            }
                          }
                        }
                      }, "Build output processor")
  }

  override fun getBuildId(): Any {
    return myBuildId
  }

  override fun append(csq: CharSequence): BuildOutputInstantReaderImpl {
    for (i in 0 until csq.length) {
      append(csq[i])
    }
    return this
  }

  override fun append(csq: CharSequence, start: Int, end: Int): BuildOutputInstantReaderImpl {
    append(csq.subSequence(start, end))
    return this
  }

  override fun append(c: Char): BuildOutputInstantReaderImpl {
    if (myBuffer == null) {
      myBuffer = StringBuilder()
    }
    if (c == '\n') {
      doFlush()
    }
    else {
      myBuffer!!.append(c)
    }
    return this
  }

  override fun close() {
    doFlush()
    try {
      myQueue.put(SHUTDOWN_PILL)
      myThread.join(TimeUnit.MINUTES.toMillis(1))
    }
    catch (ignore: InterruptedException) {
    }
    finally {
      myClosed.set(true)
    }
  }

  private fun doFlush() {
    if (myBuffer == null) {
      return
    }
    if (myClosed.get()) {
      LOG.warn("Build output reader closed")
      myBuffer!!.setLength(0)
      return
    }

    val line = myBuffer!!.toString()
    myBuffer!!.setLength(0)
    try {
      if (myStarted.compareAndSet(false, true)) {
        myThread.start()
      }
      myQueue.put(line)
    }
    catch (ignore: InterruptedException) {
      myClosed.set(true)
    }

  }

  override fun readLine(): String? {
    if (myCurrentIndex < -1) {
      LOG.error("Wrong buffered output lines index")
      myCurrentIndex = -1
    }
    if (myClosed.get()) {
      return if (myCurrentIndex > 0 && myLinesBuffer.size > myCurrentIndex) {
        myLinesBuffer[myCurrentIndex++]
      }
      else null
    }

    myCurrentIndex++
    if (myLinesBuffer.size > myCurrentIndex) {
      return myLinesBuffer[myCurrentIndex]
    }
    try {
      val line = myQueue.take()
      if (line === SHUTDOWN_PILL) {
        myClosed.set(true)
        return null
      }
      myLinesBuffer.addLast(line)
      if (myLinesBuffer.size > getMaxLinesBufferSize()) {
        myLinesBuffer.removeFirst()
        myCurrentIndex--
      }
      return line
    }
    catch (ignore: InterruptedException) {
      myClosed.set(true)
    }

    return null
  }

  override fun pushBack() = pushBack(1)

  override fun pushBack(numberOfLines: Int) {
    myCurrentIndex -= numberOfLines
  }

  override fun getCurrentLine(): String? {
    return if (myCurrentIndex >= 0 && myLinesBuffer.size > myCurrentIndex) myLinesBuffer[myCurrentIndex] else null
  }

  private class BuildOutputInstantReaderWrapper(private val myReader: BuildOutputInstantReaderImpl) : BuildOutputInstantReader {
    private var myLinesRead = 0

    override fun getBuildId(): Any = myReader.myBuildId

    override fun readLine(): String? {
      val line = myReader.readLine()
      if (line != null) myLinesRead++
      return line
    }

    override fun pushBack() = pushBack(1)

    override fun pushBack(numberOfLines: Int) {
      if (numberOfLines > myLinesRead) {
        myReader.pushBack(myLinesRead)
      }
      else {
        myReader.pushBack(numberOfLines)
      }
    }

    override fun getCurrentLine(): String? = myReader.currentLine
  }

  companion object {
    private val LOG = Logger.getInstance("#com.intellij.build.output.BuildOutputInstantReader")
    @ApiStatus.Experimental
    @TestOnly
    fun getMaxLinesBufferSize() = 50
    private const val SHUTDOWN_PILL = "Poison Pill Shutdown"
  }
}
