package com.intellij.collaboration.util

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

inline fun <reified T : Any> Project.serviceGet(): () -> T = { service() }

inline fun <reified T : Any> serviceGet(): () -> T = { service() }