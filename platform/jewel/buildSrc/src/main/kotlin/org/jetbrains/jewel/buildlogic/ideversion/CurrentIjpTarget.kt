package org.jetbrains.jewel.buildlogic.ideversion

import org.gradle.api.Project

val Project.currentIjpVersion: String
    get() {
        val rawValue =
            property("ijp.target") as? String ?: error("Property ijp.target not defined. Check your gradle.properties!")

        if (rawValue.length != 3 || rawValue.toIntOrNull()?.let { it < 0 } == true) {
            error("Invalid ijp.target property value: '$rawValue'")
        }

        return rawValue
    }
