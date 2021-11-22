package com.intellij.codeInsight.daemon.impl

import org.jetbrains.concurrency.Promise

interface AsyncDescriptionSupplier {
  fun requestDescription() : Promise<String>
}