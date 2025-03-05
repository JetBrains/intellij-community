// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs.statistics

object UserFactorDescriptions {
  private val IDS: MutableSet<String> = mutableSetOf()

  val COMPLETION_FINISH_TYPE: UserFactorDescription<CompletionFinishTypeUpdater, CompletionFinishTypeReader> =
    Descriptor.register("completionFinishedType", ::CompletionFinishTypeUpdater, ::CompletionFinishTypeReader)
  val PREFIX_LENGTH_ON_COMPLETION: UserFactorDescription<PrefixLengthUpdater, PrefixLengthReader> =
    Descriptor.register("prefixLength", ::PrefixLengthUpdater, ::PrefixLengthReader)
  val ACCEPTANCE_RATE_FACTORS: UserFactorDescription<AccRateFactorsUpdater, AccRateFactorsReader> =
    Descriptor.register("acceptanceRateFactors", ::AccRateFactorsUpdater, ::AccRateFactorsReader)
  //val TIME_BETWEEN_TYPING: UserFactorDescription<TimeBetweenTypingUpdater, TimeBetweenTypingReader> =
  //  Descriptor.register("timeBetweenTyping", ::TimeBetweenTypingUpdater, ::TimeBetweenTypingReader)

  fun isKnownFactor(id: String): Boolean = id in IDS

  private class Descriptor<out U : FactorUpdater, out R : FactorReader> private constructor(
    override val factorId: String,
    override val updaterFactory: (MutableDoubleFactor) -> U,
    override val readerFactory: (DailyAggregatedDoubleFactor) -> R,
  ) : UserFactorDescription<U, R> {
    companion object {
      fun <U : FactorUpdater, R : FactorReader> register(
        factorId: String,
        updaterFactory: (MutableDoubleFactor) -> U,
        readerFactory: (DailyAggregatedDoubleFactor) -> R,
      ): UserFactorDescription<U, R> {
        assert(!isKnownFactor(factorId)) { "Descriptor with id '$factorId' already exists" }
        IDS.add(factorId)
        return Descriptor(factorId, updaterFactory, readerFactory)
      }
    }
  }
}