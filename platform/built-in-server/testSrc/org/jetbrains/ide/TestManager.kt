package org.jetbrains.ide

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.ui.UIUtil
import org.junit.rules.TestWatcher
import org.junit.runner.Description

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

class TestManager : TestWatcher() {

  private var annotation: TestDescriptor? = null
  public var filePath: String? = null
  private var virtualFile: VirtualFile? = null

  Retention(RetentionPolicy.RUNTIME)
  Target(ElementType.METHOD)
  annotation public class TestDescriptor(public val filePath: String, public val relativeToProject: Boolean = false)

  override fun starting(description: Description?) {
    annotation = description!!.getAnnotation<TestDescriptor>(javaClass<TestDescriptor>())
    if (annotation == null) {
      return
    }

    filePath = annotation!!.filePath()

    UIUtil.invokeAndWaitIfNeeded(object : Runnable {
      override fun run() {
        val token = WriteAction.start()
        try {
          val root: VirtualFile
          if (annotation!!.relativeToProject()) {
            root = LightPlatformTestCase.getProject().getBaseDir()
          }
          else {
            root = LightPlatformTestCase.getSourceRoot()
          }
          virtualFile = root.createChildData(this@TestManager, filePath)
          LOG.info("Create " + filePath + " " + virtualFile!!.getPath())
        }
        catch (e: Exception) {
          throw RuntimeException(e)
        }
        finally {
          token.finish()
        }
      }
    })
  }

  override fun finished(description: Description?) {
    if (virtualFile == null) {
      return
    }

    UIUtil.invokeAndWaitIfNeeded(object : Runnable {
      override fun run() {
        val token = WriteAction.start()
        try {
          LOG.info("Delete " + filePath + " " + virtualFile!!.getPath())
          virtualFile!!.delete(this@TestManager)
        }
        catch (e: Exception) {
          throw RuntimeException(e)
        }
        finally {
          token.finish()
          filePath = null
          virtualFile = null
        }
      }
    })
  }

  class object {
    private val LOG = Logger.getInstance(javaClass<TestManager>())
  }
}
