// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.pointer;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.jetbrains.jsonSchema.JsonPointerUtil.*;

public final class JsonPointerPosition {

  public JsonPointerPosition() {
    this.steps = new ArrayList<>();
  }

  private JsonPointerPosition(List<Step> steps) {
    this.steps = steps;
  }

  public static JsonPointerPosition createSingleProperty(String property) {
    return new JsonPointerPosition(ContainerUtil.createMaybeSingletonList(Step.createPropertyStep(property)));
  }

  public static JsonPointerPosition parsePointer(@NotNull String pointer) {
    final List<String> chain = split(normalizeSlashes(normalizeId(pointer)));
    List<JsonPointerPosition.Step> steps = new ArrayList<>(chain.size());
    for (String s: chain) {
      try {
        steps.add(JsonPointerPosition.Step.createArrayElementStep(Integer.parseInt(s)));
      }
      catch (NumberFormatException e) {
        steps.add(JsonPointerPosition.Step.createPropertyStep(unescapeJsonPointerPart(s)));
      }
    }
    return new JsonPointerPosition(steps);
  }

  List<Step> getSteps() {
    return steps;
  }

  private final List<Step> steps;

  public void addPrecedingStep(int value) {
    steps.add(0, Step.createArrayElementStep(value));
  }

  public void addFollowingStep(int value) {
    steps.add(Step.createArrayElementStep(value));
  }

  public void addPrecedingStep(String value) {
    steps.add(0, Step.createPropertyStep(value));
  }

  public void addFollowingStep(String value) {
    steps.add(Step.createPropertyStep(value));
  }

  public void replaceStep(int pos, int value) {
    steps.set(pos, Step.createArrayElementStep(value));
  }

  public void replaceStep(int pos, String value) {
    steps.set(pos, Step.createPropertyStep(value));
  }

  public boolean isEmpty() {
    return steps.isEmpty();
  }

  public boolean isArray(int pos) {
    return checkPosInRange(pos) && steps.get(pos).isFromArray();
  }

  public boolean isObject(int pos) {
    return checkPosInRange(pos) && steps.get(pos).isFromObject();
  }

  public List<String> getStepNames() {
    return ContainerUtil.map(steps, s -> s.getName());
  }

  public @Nullable JsonPointerPosition skip(int count) {
    return checkPosInRangeIncl(count) ? new JsonPointerPosition(steps.subList(count, steps.size())) : null;
  }

  public @Nullable JsonPointerPosition trimTail(int count) {
    return checkPosInRangeIncl(count) ? new JsonPointerPosition(steps.subList(0, steps.size() - count)) : null;
  }

  public @Nullable String getLastName() {
    final Step last = ContainerUtil.getLastItem(steps);
    return last == null ? null : last.getName();
  }

  public @Nullable String getFirstName() {
    final Step last = ContainerUtil.getFirstItem(steps);
    return last == null ? null : last.getName();
  }

  public int getFirstIndex() {
    final Step last = ContainerUtil.getFirstItem(steps);
    return last == null ? -1 : last.getIdx();
  }

  public int size() {
    return steps.size();
  }

  public void updateFrom(JsonPointerPosition from) {
    steps.clear();
    steps.addAll(from.steps);
  }

  public String toJsonPointer() {
    return "/" + steps.stream().map(step -> escapeForJsonPointer(step.myName == null ? String.valueOf(step.myIdx) : step.myName)).collect(Collectors.joining("/"));
  }

  @Override
  public String toString() {
    return steps.stream().map(Object::toString).collect(Collectors.joining("->", "steps: <", ">"));
  }

  private boolean checkPosInRange(int pos) {
    return steps.size() > pos;
  }

  private boolean checkPosInRangeIncl(int pos) {
    return steps.size() >= pos;
  }

  static final class Step {
    private final @Nullable String myName;
    private final int myIdx;

    private Step(@Nullable String name, int idx) {
      myName = name;
      myIdx = idx;
    }

    public static Step createPropertyStep(final @NotNull String name) {
      return new Step(name, -1);
    }

    public static Step createArrayElementStep(final int idx) {
      assert idx >= 0;
      return new Step(null, idx);
    }

    public boolean isFromObject() {
      return myName != null;
    }

    public boolean isFromArray() {
      return myName == null;
    }

    public @Nullable String getName() {
      return myName;
    }

    public int getIdx() {
      return myIdx;
    }

    @Override
    public String toString() {
      String format = "?%s";
      if (myName != null) format = "{%s}";
      if (myIdx >= 0) format = "[%s]";
      return String.format(format, myName != null ? myName : (myIdx >= 0 ? String.valueOf(myIdx) : "null"));
    }

  }
}
