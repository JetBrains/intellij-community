package com.intellij.execution.multilaunch.design.extensions

import java.awt.Point
import java.awt.Rectangle

infix fun Point.isOver(bounds: Rectangle) = bounds.contains(this)