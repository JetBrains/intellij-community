package com.intellij.json;

import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonOriginalPsiWalker;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaResolver;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class JsonSpellcheckerStrategy extends SpellcheckingStrategy {
  private final Tokenizer<JsonStringLiteral> ourStringLiteralTokenizer = new Tokenizer<>() {
    @Override
    public void tokenize(@NotNull JsonStringLiteral element, TokenConsumer consumer) {
      final PlainTextSplitter textSplitter = PlainTextSplitter.getInstance();
      if (element.textContains('\\')) {
        final List<Pair<TextRange, String>> fragments = element.getTextFragments();
        for (Pair<TextRange, String> fragment : fragments) {
          final TextRange fragmentRange = fragment.getFirst();
          final String escaped = fragment.getSecond();
          // Fragment without escaping, also not a broken escape sequence or a unicode code point
          if (escaped.length() == fragmentRange.getLength() && !escaped.startsWith("\\")) {
            consumer.consumeToken(element, escaped, false, fragmentRange.getStartOffset(), TextRange.allOf(escaped), textSplitter);
          }
        }
      }
      else {
        consumer.consumeToken(element, textSplitter);
      }
    }
  };

  private static boolean matchesNameFromSchema(@NotNull JsonStringLiteral element) {
    final VirtualFile file = PsiUtilCore.getVirtualFile(element);
    if (file == null) return false;

    Project project = element.getProject();
    final JsonSchemaService service = JsonSchemaService.Impl.get(project);
    if (!service.isApplicableToFile(file)) return false;
    final JsonSchemaObject rootSchema = service.getSchemaObject(element.getContainingFile());
    if (rootSchema == null) return false;
    if (service.isSchemaFile(rootSchema)) {
      JsonProperty property = ObjectUtils.tryCast(element.getParent(), JsonProperty.class);
      if (property != null) {
        if (JsonSchemaObject.X_INTELLIJ_LANGUAGE_INJECTION.equals(property.getName())) {
          return true;
        }
        if ("language".equals(property.getName())) {
          PsiElement parent = property.getParent();
          if (parent instanceof JsonObject) {
            PsiElement grandParent = parent.getParent();
            if (grandParent instanceof JsonProperty && JsonSchemaObject.X_INTELLIJ_LANGUAGE_INJECTION.equals(((JsonProperty)grandParent).getName())) {
              return true;
            }
          }
        }
      }
    }

    String value = element.getValue();
    if (StringUtil.isEmpty(value)) return false;

    JsonOriginalPsiWalker walker = JsonOriginalPsiWalker.INSTANCE;
    final PsiElement checkable = walker.findElementToCheck(element);
    if (checkable == null) return false;
    final ThreeState isName = walker.isName(checkable);
    final JsonPointerPosition position = walker.findPosition(checkable, isName == ThreeState.NO);
    if (position == null || position.isEmpty() && isName == ThreeState.NO) return false;

    final Collection<JsonSchemaObject> schemas = new JsonSchemaResolver(project, rootSchema, position).resolve();
    if (schemas.isEmpty()) return false;

    return schemas.stream().anyMatch(s -> {
      if (s.getProperties().containsKey(value) || s.getMatchingPatternPropertySchema(value) != null) {
        return true;
      }
      return ContainerUtil.notNullize(s.getEnum()).stream().anyMatch(e -> e instanceof String && StringUtil.unquoteString((String)e).equals(value));
    });
  }

  @NotNull
  @Override
  public Tokenizer<?> getTokenizer(PsiElement element) {
    if (element instanceof JsonStringLiteral) {
      return matchesNameFromSchema((JsonStringLiteral)element)
        ? EMPTY_TOKENIZER
        : ourStringLiteralTokenizer;
    }
    return super.getTokenizer(element);
  }
}
