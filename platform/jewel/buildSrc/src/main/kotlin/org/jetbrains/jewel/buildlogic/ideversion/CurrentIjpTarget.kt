package org.jetbrains.jewel.buildlogic.ideversion

import org.gradle.api.Project

val Project.currentIjpVersion: String
    get() {
        val rawValue = property("bridge.ijp.target") as? String
            ?: error("Property bridge.ijp.target not defined. Check your gradle.properties!")

        if (rawValue.length != 3 || rawValue.toIntOrNull()?.let { it < 0 } == true) {
            error("Invalid bridge.ijp.target property value: '$rawValue'")
        }

        return rawValue
    }
