@file:Suppress("HardCodedStringLiteral", "DialogTitleCapitalization", "UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.kotlin

import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental.BuilderCategory
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.ModuleLevelBuilder
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler.K2JVMCompilerPerformanceManager
import org.jetbrains.kotlin.cli.pipeline.jvm.JvmCliPipeline
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.jps.model.kotlinFacet
import org.jetbrains.kotlin.progress.CompilationCanceledStatus
import java.io.File

internal class NonIncrementalKotlinBuilder(
  private val job: Job,
  private val module: JpsModule,
  private val span: Span,
) : ModuleLevelBuilder(BuilderCategory.SOURCE_PROCESSOR) {
  override fun getPresentableName() = "Kotlin Non-Incremental Builder"

  override fun getCompilableFileExtensions() = arrayListOf("kt")

  override fun build(
    context: CompileContext,
    chunk: ModuleChunk,
    dirtyFilesHolder: DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget>,
    outputConsumer: OutputConsumer
  ): ExitCode {
    val messageCollector = MessageCollectorAdapter(context, span)
    val builder = Services.Builder()
    builder.register(CompilationCanceledStatus::class.java, object : CompilationCanceledStatus {
      override fun checkCanceled() {
        job.ensureActive()
      }
    })

    val exitCode = JvmCliPipeline(K2JVMCompilerPerformanceManager()).execute(
      arguments = module.kotlinFacet!!.settings.compilerArguments!! as K2JVMCompilerArguments,
      services = builder.build(),
      originalMessageCollector = messageCollector,
    )
    if (org.jetbrains.kotlin.cli.common.ExitCode.INTERNAL_ERROR == exitCode) {
      context.processMessage(CompilerMessage("kotlin", BuildMessage.Kind.ERROR, "Internal compiler error"))
      return ExitCode.ABORT
    }

    if (Utils.ERRORS_DETECTED_KEY.get(context, false)) {
      JavaBuilderUtil.registerFilesWithErrors(context, messageCollector.filesWithErrors.map(::File))
      return ExitCode.ABORT
    }
    else {
      return ExitCode.OK
    }
  }
}