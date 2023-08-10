import java.util.List;

public class Simple {

  record StringInteger(String text, Integer number) {
  }

  public static void simple(List<StringInteger> list) {
    for (StringInteger stringInteger<caret> : list) {
      System.out.println(stringInteger.number);
      System.out.println(stringInteger.number());
      System.out.println(stringInteger.text);
      System.out.println(stringInteger.text());
      Integer number = stringInteger.number;
      System.out.println(number);
      System.out.println(number.intValue());
      String text = stringInteger.text();
      System.out.println(text);
      String text1 = stringInteger.text;
    }
  }

  public static void simpleAnnotated(List<StringInteger> list) {
    for (StringInteger stringInteger : list) {
      @Deprecated Integer number = stringInteger.number;
      String text = stringInteger.text();
    }
  }

  public static void simpleUseBefore1(List<StringInteger> list) {
    for (StringInteger stringInteger : list) {
      if (stringInteger != null) {

      }
      System.out.println(stringInteger.number);
      System.out.println(stringInteger.text);
    }
  }
  public static void simpleUseBefore2(List<StringInteger> list) {
    for (StringInteger stringInteger : list) {
      if (stringInteger.hashCode()==0) {

      }
      System.out.println(stringInteger.number);
      System.out.println(stringInteger.text);
    }
  }

  public static void notUsedVariable(List<StringInteger> list) {
    for (StringInteger stringInteger : list) {
      System.out.println(stringInteger.text);
    }
  }

  public static void convertToDifferentType(List<StringInteger> list) {
    for (StringInteger stringInteger : list) {
      CharSequence text2 = stringInteger.text;
      System.out.println(text2);
      Number number = stringInteger.number;
      System.out.println(number);
    }
  }

  public static void usedNames(List<StringInteger> list) {
    for (StringInteger stringInteger : list) {
      String text2 = stringInteger.text;
      Integer number = stringInteger.number;
      String text = "";
      System.out.println(text2);
      System.out.println(stringInteger.text);
    }
  }

  public static void extend(List<? extends StringInteger> list) {
    for (StringInteger stringInteger : list) {
      System.out.println(stringInteger.text);
      System.out.println(stringInteger.number);
    }
  }

  record GenericString<T>(T t, String text) {
  }


  public static void simpleGeneric(List<GenericString<String>> list) {
    for (GenericString<String> genericString : list) {
      System.out.println(genericString.t);
      System.out.println(genericString.text);
    }
  }

  public static void rawGeneric(List<GenericString<String>> list) {
    for (GenericString genericString : list) {
      System.out.println(genericString.t);
      System.out.println(genericString.text);
    }
  }
  public static void rawGeneric2(List<GenericString> list) {
    for (GenericString genericString : list) {
      System.out.println(genericString.t);
      System.out.println(genericString.text);
    }
  }
  public static void extendGeneric(List<GenericString<? extends String>> list) {
    for (GenericString<? extends String> genericString : list) {
      System.out.println(genericString.t);
      String t = genericString.t;
      System.out.println(genericString.text);
    }
  }

  record OuterStringInteger(StringInteger record, Integer number) {
  }

  public static void simpleOuter(List<OuterStringInteger> list) {
    for (OuterStringInteger(StringInteger stringInteger, Integer number2) : list) {
      System.out.println(stringInteger.number);
      System.out.println(stringInteger.number());
      System.out.println(stringInteger.text);
      System.out.println(stringInteger.text());
      Integer number = stringInteger.number;
      System.out.println(number);
      System.out.println(number.intValue());
      String text = stringInteger.text();
      System.out.println(text);
    }
  }

  public static void simpleUseBefore1Outer(List<OuterStringInteger> list) {
    for (OuterStringInteger(StringInteger stringInteger, Integer number2) : list) {
      if (stringInteger != null) {

      }
      System.out.println(stringInteger.number);
      System.out.println(stringInteger.text);
    }
  }
  public static void simpleUseBefore2Outer(List<OuterStringInteger> list) {
    for (OuterStringInteger(StringInteger stringInteger, Integer number2) : list) {
      if (stringInteger.hashCode()==0) {

      }
      System.out.println(stringInteger.number);
      System.out.println(stringInteger.text);
    }
  }

  public static void notUsedVariableOuter(List<OuterStringInteger> list) {
    for (OuterStringInteger(StringInteger stringInteger, Integer number2) : list) {
      System.out.println(stringInteger.text);
    }
  }

  public static void convertToDifferentTypeOuter(List<OuterStringInteger> list) {
    for (OuterStringInteger(StringInteger stringInteger, Integer number2) : list) {
      CharSequence text2 = stringInteger.text;
      System.out.println(text2);
      Number number = stringInteger.number;
      System.out.println(number);
    }
  }

  public static void usedNamesOuter(List<OuterStringInteger> list) {
    for (OuterStringInteger(StringInteger stringInteger, Integer number2) : list) {
      String text2 = stringInteger.text;
      Integer number = stringInteger.number;
      String text = "";
      System.out.println(text2);
      System.out.println(stringInteger.text);
    }
  }


  record GenericStringOuter<T>(GenericString<T> genericString, String text2) {
  }

  public static void simpleGenericOuter(List<GenericStringOuter<String>> list) {
    for (GenericStringOuter(GenericString<String> genericString, String text2) : list) {
      System.out.println(genericString.t);
      System.out.println(genericString.text);
    }
  }

  public static void rawGenericOuter(List<GenericStringOuter<String>> list) {
    for (GenericStringOuter(GenericString genericString, String text2) : list) {
      System.out.println(genericString.t);
      System.out.println(genericString.text);
    }
  }

  public static void simpleGenericOuterVar(List<GenericStringOuter<String>> list) {
    for (GenericStringOuter(GenericString(String t, String text), String text2) : list) {
      System.out.println(t);
      System.out.println(text);
    }
  }
}
