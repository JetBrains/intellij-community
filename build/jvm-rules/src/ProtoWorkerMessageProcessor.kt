package org.jetbrains.bazel.jvm

import com.google.devtools.build.lib.worker.WorkerProtocol.WorkRequest
import com.google.devtools.build.lib.worker.WorkerProtocol.WorkResponse
import java.io.InputStream
import java.io.OutputStream

internal class ProtoWorkerMessageProcessor(
  private val input: InputStream,
  private val output: OutputStream
) {
  fun readWorkRequest(): WorkRequest? = WorkRequest.parseDelimitedFrom(input)

  fun writeWorkResponse(workResponse: WorkResponse) {
    try {
      workResponse.writeDelimitedTo(output)
    }
    finally {
      output.flush()
    }
  }
}