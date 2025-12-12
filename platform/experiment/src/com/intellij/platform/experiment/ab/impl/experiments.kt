// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab.impl

import com.intellij.internal.statistic.eventLog.fus.MachineIdManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.platform.experiment.ab.impl.ABExperimentOption.TYPESCRIPT_SERVICE_TYPES
import com.intellij.platform.experiment.ab.impl.ABExperimentOption.UNASSIGNED
import com.intellij.platform.experiment.ab.impl.statistic.ABExperimentCountCollector
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.VisibleForTesting
import java.util.*
import kotlin.math.absoluteValue

/**
 * Complete list of all available AB experiments.
 *
 * The plugins are welcome to use [ABExperimentOption.isEnabled] to check whether the experiment is enabled on the user's machine
 */
enum class ABExperimentOption {
  KUBERNETES_SEPARATE_SERVICE_VIEW,
  FUZZY_FILE_SEARCH,
  SHOW_TRIAL_SURVEY,
  NEW_USERS_ONBOARDING,
  TYPESCRIPT_SERVICE_TYPES,
  SPLIT_SEARCH_EVERYWHERE,

  /**
   * A group for users which are not assigned to any experiment.
   */
  UNASSIGNED;

  fun isEnabled(): Boolean {
    require(this != UNASSIGNED) {
      "UNASSIGNED experiment option is not supposed to be used in the isEnabled() method"
    }
    val decision = thisUserDecision
    if (decision.option != UNASSIGNED) {
      ABExperimentCountCollector.logABExperimentOptionUsed(decision)
    }
    return isAllowed(decision.option) && decision.option == this && !decision.isControlGroup
  }
}

/**
 * Total number of "containers" where users are distributed. Each user belongs exactly to one bucket.
 * Each bucket has at most one experimental option enabled, which are controlled by [experimentsPartition].
 */
internal const val NUMBER_OF_BUCKETS: Int = 1024

/**
 * Mapping of buckets to experiments.
 * Each experiment is assigned a non-overlapping range of buckets in [0..[NUMBER_OF_BUCKETS])
 * The buckets that are not assigned to any experiment are considered to be in the UNASSIGNED experiment (i.e., no experiments are enabled for them).
 */
@VisibleForTesting
internal val experimentsPartition: List<ExperimentAssignment> = listOf(
  //ExperimentAssignment(
  //  experiment = KUBERNETES_SEPARATE_SERVICE_VIEW,
  //  experimentBuckets = (0 until 128).toSet(),
  //  controlBuckets = (128 until 256).toSet(),
  //  majorVersion = "2025.2"
  //),
  ExperimentAssignment(
    experiment = TYPESCRIPT_SERVICE_TYPES,
    experimentBuckets = (0 until 512).toSet(),
    controlBuckets = (512 until 1024).toSet(),
    majorVersion = "2025.3 EAP",
    products = EnumSet.of(IntelliJPlatformProduct.WEBSTORM),
  ),
  ExperimentAssignment(
    experiment = ABExperimentOption.SPLIT_SEARCH_EVERYWHERE,
    experimentBuckets = (0 until 512).toSet(),
    controlBuckets = (512 until 1024).toSet(),
    majorVersion = "2026.1 EAP",
    products = EnumSet.of(IntelliJPlatformProduct.IDEA,
                          IntelliJPlatformProduct.PYCHARM,
                          IntelliJPlatformProduct.RIDER),
  ),
  // the rest belongs to the "unassigned" experiment
)

/**
 * This method can be configured to allow options only in particular IDEs.
 */
fun isAllowed(option: ABExperimentOption): Boolean = when (option) {
  ABExperimentOption.SPLIT_SEARCH_EVERYWHERE -> IdeProductMode.isMonolith
  else -> true
}

// ================= IMPLEMENTATION ====================

internal data class ABExperimentDecision(val option: ABExperimentOption, val isControlGroup: Boolean, val bucketNumber: Int)

internal fun ABExperimentOption.reportableName(): String {
  return toString().lowercase().replace('_', '.')
}

private val thisUserDecision: ABExperimentDecision by lazy {
  val currentBucket = getUserBucketNumber()
  val currentVersion = ApplicationInfo.getInstance().fullVersion
  val currentProduct = IntelliJPlatformProduct.get()
  val option = experimentsPartition.find {
    (it.majorVersion == null || currentVersion.startsWith(it.majorVersion)) &&
    (it.experimentBuckets.contains(currentBucket) || it.controlBuckets.contains(currentBucket)) &&
    it.products.contains(currentProduct)
  } ?: return@lazy ABExperimentDecision(option = UNASSIGNED, isControlGroup = true, bucketNumber = currentBucket)
  ABExperimentDecision(option = option.experiment, isControlGroup = option.controlBuckets.contains(currentBucket), bucketNumber = currentBucket)
}

internal data class ExperimentAssignment(
  val experiment: ABExperimentOption,
  val experimentBuckets: Set<Int>,
  val controlBuckets: Set<Int>,
  val majorVersion: String? = null,
  val products: Set<IntelliJPlatformProduct> = EnumSet.allOf(IntelliJPlatformProduct::class.java)
)

internal fun getUserBucketNumber(): Int {
  val overridingBucket = Integer.getInteger("ide.ab.test.overriding.bucket")
  if (overridingBucket != null) {
    LOG.info("Overriding bucket number: $overridingBucket")
    return overridingBucket
  }
  val deviceId = LOG.runAndLogException {
    MachineIdManager.getAnonymizedMachineId(getDeviceIdPurpose())
  }

  val bucketNumber = deviceId.hashCode().absoluteValue % NUMBER_OF_BUCKETS
  LOG.debug { "User bucket number is: $bucketNumber." }
  return bucketNumber
}

private fun getDeviceIdPurpose(): String {
  return "A/B Experiment" + ApplicationInfo.getInstance().shortVersion
}

private val LOG = logger<ABExperimentOption>()

/**
 * There is no need to list all available products. Add if a product-specific experiment is needed.
 */
internal enum class IntelliJPlatformProduct {
  WEBSTORM,
  IDEA,
  PYCHARM,
  RIDER,
  OTHER,
  ;

  companion object {
    fun get(): IntelliJPlatformProduct =
      when {
        PlatformUtils.isWebStorm() -> WEBSTORM
        PlatformUtils.isIdeaCommunity() || PlatformUtils.isIdeaUltimate() -> IDEA
        PlatformUtils.isPyCharm() -> PYCHARM
        PlatformUtils.isRider() -> RIDER
        else -> OTHER
      }
  }
}