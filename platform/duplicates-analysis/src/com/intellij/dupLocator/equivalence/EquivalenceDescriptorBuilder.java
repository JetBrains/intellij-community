package com.intellij.dupLocator.equivalence;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EquivalenceDescriptorBuilder implements EquivalenceDescriptor {
  private final List<SingleChildDescriptor> mySingleChildDescriptors = new ArrayList<>();
  private final List<MultiChildDescriptor> myMultiChildDescriptors = new ArrayList<>();
  private final List<Object> myConstants = new ArrayList<>();
  private final List<PsiElement[]> myCodeBlocks = new ArrayList<>();

  public EquivalenceDescriptorBuilder() {
  }

  @Override
  public List<SingleChildDescriptor> getSingleChildDescriptors() {
    return mySingleChildDescriptors;
  }

  @Override
  public List<MultiChildDescriptor> getMultiChildDescriptors() {
    return myMultiChildDescriptors;
  }

  @Override
  public List<Object> getConstants() {
    return myConstants;
  }

  @Override
  @NotNull
  public List<PsiElement[]> getCodeBlocks() {
    return myCodeBlocks;
  }

  public EquivalenceDescriptorBuilder codeBlock(PsiElement @Nullable [] block) {
    myCodeBlocks.add(block);
    return this;
  }

  public EquivalenceDescriptorBuilder element(@Nullable PsiElement element) {
    return add(SingleChildDescriptor.MyType.DEFAULT, element);
  }

  public EquivalenceDescriptorBuilder elements(PsiElement @Nullable [] elements) {
    return add(MultiChildDescriptor.MyType.DEFAULT, elements);
  }

  public EquivalenceDescriptorBuilder children(@Nullable PsiElement element) {
    return add(SingleChildDescriptor.MyType.CHILDREN, element);
  }

  @NotNull
  public EquivalenceDescriptorBuilder optionally(@Nullable PsiElement element) {
    return add(SingleChildDescriptor.MyType.OPTIONALLY, element);
  }

  @NotNull
  public EquivalenceDescriptorBuilder optionallyInPattern(@Nullable PsiElement element) {
    return add(SingleChildDescriptor.MyType.OPTIONALLY_IN_PATTERN, element);
  }

  @NotNull
  public EquivalenceDescriptorBuilder optionally(PsiElement @Nullable [] elements) {
    return add(MultiChildDescriptor.MyType.OPTIONALLY, elements);
  }

  @NotNull
  public EquivalenceDescriptorBuilder optionallyInPattern(PsiElement @Nullable [] elements) {
    return add(MultiChildDescriptor.MyType.OPTIONALLY_IN_PATTERN, elements);
  }

  @NotNull
  public EquivalenceDescriptorBuilder childrenOptionally(@Nullable PsiElement element) {
    return add(SingleChildDescriptor.MyType.CHILDREN_OPTIONALLY, element);
  }

  @NotNull
  public EquivalenceDescriptorBuilder childrenOptionallyInPattern(@Nullable PsiElement element) {
    return add(SingleChildDescriptor.MyType.CHILDREN_OPTIONALLY_IN_PATTERN, element);
  }

  @NotNull
  public EquivalenceDescriptorBuilder inAnyOrder(PsiElement[] elements) {
    return add(MultiChildDescriptor.MyType.IN_ANY_ORDER, elements);
  }

  @NotNull
  public EquivalenceDescriptorBuilder childrenInAnyOrder(@Nullable PsiElement element) {
    return add(SingleChildDescriptor.MyType.CHILDREN_IN_ANY_ORDER, element);
  }

  @NotNull
  public EquivalenceDescriptorBuilder constant(@Nullable Object constant) {
    myConstants.add(constant);
    return this;
  }

  private EquivalenceDescriptorBuilder add(MultiChildDescriptor.MyType type, PsiElement[] elements) {
    myMultiChildDescriptors.add(new MultiChildDescriptor(type, elements));
    return this;
  }

  private EquivalenceDescriptorBuilder add(SingleChildDescriptor.MyType type, PsiElement element) {
    mySingleChildDescriptors.add(new SingleChildDescriptor(type, element));
    return this;
  }
}
