// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.*
import fleet.reporting.shared.tracing.span
import fleet.reporting.shared.tracing.spannedScope

suspend fun <T> hackyNonBlockingChange(body: ChangeScope.() -> T): T =
  spannedScope("hackyNonBlockingChange") {
    val kernel = transactor()
    val middleware = kernel.middleware
    var res: T? = null
    val db = db()
    val insn = span("run change in background") {
      buildList {
        db.change(defaultPart = 1) {
          middleware.run {
            performChange {
              DbContext.threadBound.ensureMutable {
                res = alter(impl.collectingInstructions(this@buildList::add)) {
                  body()
                }
              }
            }
          }
        }
      }
    }
    spannedScope("applyChange") {
      kernel.changeAsync {
        span("playInstructions") {
          insn.forEach(this::mutate)
        }
      }.await()
    }
    @Suppress("UNCHECKED_CAST")
    res as T
  }
