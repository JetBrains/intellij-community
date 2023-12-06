// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

/**
 * For statistics purposes only.
 * When nothing suits, use `Other`
 */
public enum DumbModeBlockedFunctionality {
  Other,
  Action,
  ActionWithoutId,
  MultipleActionIds,
  UsageInfoSearcherAdapter,
  Refactoring,
  MemberInplaceRenamer,
  PackageDependencies,
  RemoteDebuggingFileFinder,
  CtrlMouseHandler,
  GotoClass,
  GotoDeclaration,
  GotoDeclarationOnly,
  GotoDeclarationOrUsage,
  GotoTarget,
  GotoTypeDeclaration,
  GotoImplementations,
  LineProfiler,
  JfrStackFrames,
  RDClientHyperlink,
  Spring,
  TmsFilter,
  Kotlin,
  Android,
  Uml,
  GroovyMarkers,
  DupLocator,
  Intentions,
  FrameworkDetection,
  EditorGutterComponent,
  CodeCompletion,
  FindUsages,
  Gwt,
  GlobalInspectionContext,
  PostCommitCheck,
  SearchEverywhere,
  ProjectView,
  SafeDeleteDialog,
  RefactoringDialog
}