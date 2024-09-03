package com.intellij.notebooks.visualization.ui

import com.intellij.openapi.actionSystem.AnAction

interface HasGutterIcon {
  fun updateGutterIcons(gutterAction: AnAction?)
}