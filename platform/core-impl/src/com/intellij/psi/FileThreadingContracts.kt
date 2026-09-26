// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier

@ApiStatus.Internal
object FileThreadingContracts {
  private val LOG = Logger.getInstance(FileThreadingContracts::class.java)

  private val REQUIRING_APPLICATION_LOCK = Key.create<Boolean?>("EXPOSED_IN_EDITOR")

  /**
   * Once a non-physical/light file gets "published", it gets visible to clients that rely on the read-lock/write-lock threading contract.
   * From this point on the file must obey the write-lock contract like a physical file, so mark it as exposed;
   */
  @JvmStatic
  fun markLightFileRequiresApplicationLockOnModification(file: LightVirtualFile) {
    if (Registry.`is`("psi.assert.wl.for.exposed.light.files", true)) {
      file.putUserData(REQUIRING_APPLICATION_LOCK, true)
    }
  }

  /**
   * @return whether `vFile` is a non-physical (light) file that has been published to a real editor and therefore must
   * obey the write-lock contract like a physical file. See [.EXPOSED_IN_EDITOR] and IJPL-249128.
   */
  @JvmStatic
  fun requiresApplicationLockForModifications(vFile: VirtualFile): Boolean {
    return vFile.getUserData(REQUIRING_APPLICATION_LOCK) == true
  }

  /**
   * The single write-lock checkpoint for PSI modifications of files that must obey the write-lock contract (a physical file,
   * or a light file [exposed in an editor][.isExposedInEditor]; DummyHolder and injected files are exempt and must be
   * filtered out by the caller). If the write lock is not held, reports it as a soft error with the file's creation trace
   * attached for triage. The message is computed lazily, so the check stays cheap on the hot PSI-modification path.
   *
   *
   * Shared by the tree-mutation checkpoint (`CompositeElement.assertThreading`) and the view-provider drop/replace
   * checkpoint (`FileManagerImpl.setViewProvider`). See IJPL-249128.
   */
  @JvmStatic
  fun assertPsiModificationHasWriteAccess(vFile: VirtualFile, message: Supplier<String>) {
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      return
    }

    val text = message.get()
    val creationTrace = PsiInvalidElementAccessException.getCreationTrace(vFile)
    if (creationTrace != null) {
      val attachment = if (creationTrace is Throwable) {
        Attachment("lightFileCreationTrace", creationTrace)
      }
      else {
        Attachment("lightFileCreationTrace.txt", creationTrace.toString())
      }
      LOG.error(RuntimeExceptionWithAttachments(text, attachment))
    }
    else {
      LOG.error(text)
    }
  }

  /**
   * A non-physical (light) file that has been exposed to a real editor must not have its PSI dropped/replaced without the
   * write lock: doing so invalidates PSI already published to editor/document listeners that rely on the read-lock/write-lock
   * contract (IJPL-249128). This mirrors the write-access requirement already enforced for event-system-enabled files (see the
   * non-light branch of [.changeFileProvider] and [.forceReload]); light files used to skip it entirely.
   */
  @JvmStatic
  fun assertWriteAccessForExposedLightFile(vFile: VirtualFile) {
    if (vFile is LightVirtualFile && requiresApplicationLockForModifications(vFile)) {
      assertPsiModificationHasWriteAccess(vFile) { "Threading assertion. Non-physical file exposed in an editor must not have its PSI reset/replaced without a write lock: $vFile" }
    }
  }
}
