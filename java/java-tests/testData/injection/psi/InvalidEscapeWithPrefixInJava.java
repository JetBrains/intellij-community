import java.util.regex.Pattern;

public class MyClass {

  private final String BASE64_INDEX = "[a-zA-Z0-9[+][/]]";
  //language=RegExp
  private final Pattern MY_PATTERN = BASE64_INDEX + "{14}\Z";
}
