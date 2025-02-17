package com.intellij.database.loaders

import com.intellij.database.extensions.DataConsumer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.function.Consumer

interface DataLoader {
  fun getId(): @NonNls String
  fun getDisplayName(): @Nls String

  fun isTableFirstFormat(): Boolean
  fun isSuitable(file: VirtualFile): Boolean
  fun getAssociatedExtensions(): Iterable<String>

  @Throws(Exception::class)
  fun loadFromFile(project: Project, file: VirtualFile, dataConsumer: DataConsumer)
}