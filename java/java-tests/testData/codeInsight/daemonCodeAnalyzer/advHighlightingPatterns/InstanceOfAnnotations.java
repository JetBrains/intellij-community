import java.lang.annotation.*;

class X {
  @Target(ElementType.LOCAL_VARIABLE)
  @interface LocalAnno {}

  @Target(ElementType.TYPE_USE)
  @interface TypeAnno {}

  @Target(ElementType.PARAMETER)
  @interface ParamAnno {}
  
  void test(Object obj) {
    if (obj instanceof @LocalAnno String s) {}
    if (obj instanceof @TypeAnno String s) {}
    if (obj instanceof <error descr="'@ParamAnno' not applicable to local variable">@ParamAnno</error> String s) {}
    if (obj instanceof <error descr="'@LocalAnno' not applicable to type use">@LocalAnno</error> String) {}
    if (obj instanceof @TypeAnno String) {}
    if (obj instanceof <error descr="'@ParamAnno' not applicable to type use">@ParamAnno</error> String) {}
  }
}