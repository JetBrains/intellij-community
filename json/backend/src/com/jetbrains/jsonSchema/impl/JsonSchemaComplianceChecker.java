// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.diagnostic.telemetry.helpers.TraceKt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SmartList;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.fus.JsonSchemaHighlightingSessionStatisticsCollector;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JsonSchemaComplianceChecker {
  private static final Logger LOG = Logger.getInstance(JsonSchemaComplianceChecker.class);
  private static final Key<Set<PsiElement>> ANNOTATED_PROPERTIES = Key.create("JsonSchema.Properties.Annotated");

  private final @NotNull JsonSchemaObject myRootSchema;
  private final @NotNull ProblemsHolder myHolder;
  private final @NotNull JsonLikePsiWalker myWalker;
  private final LocalInspectionToolSession mySession;
  private final @NotNull JsonComplianceCheckerOptions myOptions;
  private final @Nullable @Nls String myMessagePrefix;

  private int myErrorCount;

  public JsonSchemaComplianceChecker(@NotNull JsonSchemaObject rootSchema,
                                     @NotNull ProblemsHolder holder,
                                     @NotNull JsonLikePsiWalker walker,
                                     @NotNull LocalInspectionToolSession session,
                                     @NotNull JsonComplianceCheckerOptions options) {
    this(rootSchema, holder, walker, session, options, null);
  }

  public JsonSchemaComplianceChecker(@NotNull JsonSchemaObject rootSchema,
                                     @NotNull ProblemsHolder holder,
                                     @NotNull JsonLikePsiWalker walker,
                                     @NotNull LocalInspectionToolSession session,
                                     @NotNull JsonComplianceCheckerOptions options,
                                     @Nullable @Nls String messagePrefix) {
    myRootSchema = rootSchema;
    myHolder = holder;
    myWalker = walker;
    mySession = session;
    myOptions = options;
    myMessagePrefix = messagePrefix;
  }

  public void annotate(final @NotNull PsiElement element) {
    boolean useNetworknt = Registry.is("json.schema.use.networknt.validation", false);
    withAnnotateTrace(element, useNetworknt, () -> {
      if (useNetworknt) {
        doAnnotateWithNetworknt(element);
      }
      else {
        doAnnotateWithOldEngine(element);
      }
    });
  }

  private void doAnnotateWithOldEngine(@NotNull PsiElement element) {
    JsonSchemaHighlightingSessionStatisticsCollector.getInstance().recordSchemaFeaturesUsage(myRootSchema, () -> doAnnotate(element));
  }

  private void doAnnotateWithNetworknt(@NotNull PsiElement element) {
    Project project = element.getProject();
    VirtualFile schemaFile = myRootSchema.getRawFile();
    if (schemaFile == null) {
      LOG.error("networknt validation skipped: schema has no raw file, class=" + myRootSchema.getClass().getSimpleName());
      return;
    }

    NetworkntValidationBridge validator = NetworkntValidationBridge.getInstance(project);

    JsonSchemaService schemaService = JsonSchemaService.Impl.get(project);
    JsonSchemaVersion schemaVersion = schemaService.getSchemaVersion(schemaFile);
    if (schemaVersion == null) {
      schemaVersion = JsonSchemaVersion.SCHEMA_7;
    }

    Map<PsiElement, JsonValidationError> errors = validator.validate(schemaFile, myWalker, element, schemaVersion);

    createWarnings(new JsonSchemaAnnotatorChecker(project, myOptions, errors));
  }

  private void doAnnotate(@NotNull PsiElement element) {
    Project project = element.getProject();
    final JsonPropertyAdapter firstProp = myWalker.getParentPropertyAdapter(element);
    if (firstProp != null) {
      final JsonPointerPosition position = myWalker.findPosition(firstProp.getDelegate(), true);
      if (position == null || position.isEmpty()) {
        return;
      }
      final MatchResult result = new JsonSchemaResolver(project, myRootSchema, position, firstProp.getNameValueAdapter()).detailedResolve();
      for (JsonValueAdapter value : firstProp.getValues()) {
        createWarnings(JsonSchemaAnnotatorChecker.checkByMatchResult(project, value, result, myOptions));
      }
    }
    checkRoot(element, firstProp);
  }

  private void checkRoot(@NotNull PsiElement element, @Nullable JsonPropertyAdapter firstProp) {
    JsonValueAdapter rootToCheck;
    if (firstProp == null) {
      rootToCheck = findTopLevelElement(myWalker, element);
    }
    else {
      rootToCheck = firstProp.getParentObject();
      if (rootToCheck == null || !myWalker.isTopJsonElement(rootToCheck.getDelegate().getParent())) {
        return;
      }
    }
    if (rootToCheck != null) {
      Project project = element.getProject();
      final MatchResult matchResult =
        new JsonSchemaResolver(project, myRootSchema, new JsonPointerPosition(), rootToCheck).detailedResolve();
      createWarnings(JsonSchemaAnnotatorChecker.checkByMatchResult(project, rootToCheck, matchResult, myOptions));
    }
  }

  @ApiStatus.Internal
  protected void createWarnings(@Nullable JsonSchemaAnnotatorChecker checker) {
    if (checker == null || checker.isCorrect()) return;
    myErrorCount += checker.getErrors().size();
    // compute intersecting ranges - we'll solve warning priorities based on this information
    List<TextRange> ranges = new ArrayList<>();
    List<List<Map.Entry<PsiElement, JsonValidationError>>> entries = new ArrayList<>();
    for (Map.Entry<PsiElement, JsonValidationError> entry : checker.getErrors().entrySet()) {
      TextRange range = myWalker.adjustErrorHighlightingRange(entry.getKey());
      boolean processed = false;
      for (int i = 0; i < ranges.size(); i++) {
        TextRange currRange = ranges.get(i);
        if (currRange.intersects(range)) {
          ranges.set(i, new TextRange(Math.min(currRange.getStartOffset(), range.getStartOffset()),
                                      Math.max(currRange.getEndOffset(), range.getEndOffset())));
          entries.get(i).add(entry);
          processed = true;
          break;
        }
      }
      if (processed) continue;

      ranges.add(range);
      entries.add(new SmartList<>(entry));
    }

    // for each set of intersecting ranges, compute the best errors to show
    for (List<Map.Entry<PsiElement, JsonValidationError>> entryList : entries) {
      int min = entryList.stream().map(v -> v.getValue().getPriority().ordinal()).min(Integer::compareTo).orElse(Integer.MAX_VALUE);
      for (Map.Entry<PsiElement, JsonValidationError> entry : entryList) {
        JsonValidationError validationError = entry.getValue();
        PsiElement psiElement = entry.getKey();
        if (validationError.getPriority().ordinal() > min) {
          continue;
        }
        TextRange range = myWalker.adjustErrorHighlightingRange(psiElement);
        range = range.shiftLeft(psiElement.getTextRange().getStartOffset());
        registerError(psiElement, range, validationError);
      }
    }
  }

  private void registerError(@NotNull PsiElement psiElement, @NotNull TextRange range, @NotNull JsonValidationError validationError) {
    if (checkIfAlreadyProcessed(psiElement)) return;
    String value = validationError.getMessage();
    if (myMessagePrefix != null) value = myMessagePrefix + value;
    LocalQuickFix[] fix = validationError.createFixes(myWalker.getSyntaxAdapter(myHolder.getProject()));
    PsiElement element = range.isEmpty() ? psiElement.getContainingFile() : psiElement;
    if (fix.length == 0) {
      myHolder.registerProblem(element, range, value);
    }
    else {
      myHolder.registerProblem(element, range, value, fix);
    }
  }

  private static JsonValueAdapter findTopLevelElement(@NotNull JsonLikePsiWalker walker, @NotNull PsiElement element) {
    final Ref<PsiElement> ref = new Ref<>();
    PsiTreeUtil.findFirstParent(element, el -> {
      final boolean isTop = walker.isTopJsonElement(el);
      if (!isTop) ref.set(el);
      return isTop;
    });
    return ref.isNull() ? (walker.acceptsEmptyRoot() ? walker.createValueAdapter(element) : null) : walker.createValueAdapter(ref.get());
  }

  private boolean checkIfAlreadyProcessed(@NotNull PsiElement property) {
    Set<PsiElement> data =
      ConcurrencyUtil.computeIfAbsent(mySession, ANNOTATED_PROPERTIES, () -> ConcurrentCollectionFactory.createConcurrentSet());
    return !data.add(property);
  }

  // Trace helper method for annotation processing
  private void withAnnotateTrace(@NotNull PsiElement element, boolean useNetworknt, @NotNull Runnable doAnnotate) {
    TraceKt.use(
      JsonSchemaTracingKt.getJsonSchemaTracer().spanBuilder("ij.annotate")
        .setAttribute("file", element.getContainingFile().getName())
        .setAttribute("useNetworknt", String.valueOf(useNetworknt))
        .setAttribute("schemaFile", myRootSchema.getRawFile() != null ? myRootSchema.getRawFile().getName() : "null")
        .setAttribute("schemaClass", myRootSchema.getClass().getSimpleName()),
      span -> {
        doAnnotate.run();
        span.setAttribute("error_count", myErrorCount);
        return null;
      });
  }
}
