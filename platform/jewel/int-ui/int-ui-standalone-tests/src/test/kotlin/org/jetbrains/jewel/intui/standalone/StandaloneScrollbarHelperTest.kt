// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jetbrains.jewel.intui.standalone.fakes.FakeMacPlatformServices
import org.jetbrains.jewel.intui.standalone.styling.default
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

internal class StandaloneScrollbarHelperTest {
    private lateinit var fakePlatformServices: FakeMacPlatformServices
    private lateinit var helper: StandaloneScrollbarHelper

    @BeforeEach
    fun setup() {
        fakePlatformServices = FakeMacPlatformServices()
        helper = StandaloneScrollbarHelper(fakePlatformServices)
    }

    @Test
    fun `should update track click behavior when system preferences change`() {
        fakePlatformServices.simulateSystemChange(TrackClickBehavior.NextPage)

        assertEquals(TrackClickBehavior.NextPage, helper.trackClickBehaviorFlow.value)
    }

    @Test
    fun `should update scrollbar visibility when system preferences change`() {
        val scrollBarVisibility = ScrollbarVisibility.WhenScrolling.default()
        fakePlatformServices.simulateSystemChange(newVisibility = scrollBarVisibility)

        assertEquals(scrollBarVisibility, helper.scrollbarVisibilityStyleFlow.value)
    }

    @Test
    @DisabledOnOs(OS.MAC)
    fun `on non-macOS platforms should return default values`() {
        val visibility = helper.scrollbarVisibilityStyle
        val behavior = helper.trackClickBehavior

        assertTrue(
            visibility is ScrollbarVisibility.AlwaysVisible,
            "Non-macOS should default to AlwaysVisible, got: $visibility",
        )
        assertEquals(TrackClickBehavior.JumpToSpot, behavior, "Non-macOS should default to JumpToSpot")
    }

    @Test
    @EnabledOnOs(OS.MAC)
    fun `on macOS should read system preferences`() {
        val visibility = helper.scrollbarVisibilityStyle
        val behavior = helper.trackClickBehavior

        assertTrue(
            visibility is ScrollbarVisibility.AlwaysVisible || visibility is ScrollbarVisibility.WhenScrolling,
            "macOS scrollbar visibility should be either AlwaysVisible or WhenScrolling, got: $visibility",
        )
        assertTrue(
            behavior == TrackClickBehavior.JumpToSpot || behavior == TrackClickBehavior.NextPage,
            "macOS track click behavior should be either JumpToSpot or NextPage, got: $behavior",
        )
    }

    @Test
    fun `state flows should maintain consistency with direct property access`() {
        runTest {
            val flowVisibility = helper.scrollbarVisibilityStyleFlow.first()
            val directVisibility = helper.scrollbarVisibilityStyle

            val flowBehavior = helper.trackClickBehaviorFlow.first()
            val directBehavior = helper.trackClickBehavior

            assertEquals(
                flowVisibility::class,
                directVisibility::class,
                "Flow and direct visibility should be of the same type",
            )
            assertEquals(flowBehavior, directBehavior, "Flow and direct behavior should match")
        }
    }

    @Test
    fun `callback should push new system values into flows`() {
        runTest {
            fakePlatformServices.simulateSystemChange(newBehavior = TrackClickBehavior.NextPage)
            assertEquals(TrackClickBehavior.NextPage, helper.trackClickBehaviorFlow.value)

            fakePlatformServices.simulateSystemChange(newBehavior = TrackClickBehavior.JumpToSpot)
            assertEquals(TrackClickBehavior.JumpToSpot, helper.trackClickBehaviorFlow.value)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `multiple subscribers should all receive the latest updates`() {
        runTest {
            val results1 = mutableListOf<TrackClickBehavior>()
            val results2 = mutableListOf<TrackClickBehavior>()

            val job1 = launch { helper.trackClickBehaviorFlow.toList(results1) }
            fakePlatformServices.simulateSystemChange(TrackClickBehavior.NextPage)
            val job2 = launch { helper.trackClickBehaviorFlow.toList(results2) }

            advanceUntilIdle()

            assertEquals(TrackClickBehavior.NextPage, results1.last())
            assertEquals(TrackClickBehavior.NextPage, results2.last())

            job1.cancel()
            job2.cancel()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `emitting same value twice wont trigger collector a second time`() {
        runTest {
            val helper = StandaloneScrollbarHelper(fakePlatformServices)
            val whenScrollingVisibility = ScrollbarVisibility.WhenScrolling.default()
            val flowEmissions = mutableListOf<ScrollbarVisibility>()

            val collectJob =
                launch(UnconfinedTestDispatcher()) { helper.scrollbarVisibilityStyleFlow.toList(flowEmissions) }

            fakePlatformServices.simulateSystemChange(newVisibility = whenScrollingVisibility)
            fakePlatformServices.simulateSystemChange(newVisibility = whenScrollingVisibility)

            advanceUntilIdle()

            assertEquals(2, flowEmissions.size)
            assertEquals(ScrollbarVisibility.AlwaysVisible.default(), flowEmissions.first())
            assertEquals(whenScrollingVisibility, flowEmissions.last())

            collectJob.cancel()
        }
    }
}
