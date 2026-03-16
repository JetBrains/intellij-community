package staticImportCanBeUsed;

import java.util.Locale;

@SuppressWarnings("unused")
final class ExampleImpls {

  record ExampleImpl(String translationKey) implements Example {

    @Override
    public String localize(Locale locale) {
      return StaticMethod.localize(locale, translationKey);
    }
  }
}