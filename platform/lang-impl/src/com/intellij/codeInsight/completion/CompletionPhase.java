/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.completion;

/**
 * @author peter
 */
public abstract class CompletionPhase {
  public static final CompletionPhase NoCompletion = new CompletionPhase() {};

  public static class AutoPopupAlarm extends CompletionPhase {}
  public static class Synchronous extends CompletionPhase {}
  public static class BgCalculation extends CompletionPhase {}
  public static class ItemsCalculated extends CompletionPhase {}
  public static class Restarted extends CompletionPhase {}
  public static class InsertedSingleItem extends CompletionPhase {}
  public static class NoSuggestionsHint extends CompletionPhase {}
  public static class PossiblyDisturbingAutoPopup extends CompletionPhase {}
  public static class EmptyAutoPopup extends CompletionPhase {}

}
