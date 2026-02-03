import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

class Test {
  enum TestEnum {EnumOne, EnumTwo, EnumThree}

  public static void main(String[] args) {
    String testString = "EnumOne=0, EnumTwo=2,EnumThree=1";

    Map<TestEnum, Integer> enumMap = Optional.ofNullable(testString)
      .map(levelsString -> Arrays.stream(levelsString.split("\\s*,\\s*"))
        .map(splitStringComponent -> splitStringComponent.split("="))
        .filter(keyValArray -> keyValArray.length == 2)
        .collect(Collectors.toMap(keyValArray -> TestEnum.valueOf(keyValArray[0]), s -> Integer.valueOf(s[1]))))
      .orElse(new HashMap<>());

    System.out.println(enumMap);
  }
}