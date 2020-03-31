import java.util.*;
import java.util.stream.*;

class MyTest {
    void m(Map<String, BladeInjectionInfo> directiveInfos){
        Map<String, BladeInjectionInfo> lowerCaseDirectiveInfos = directiveInfos.entrySet().stream()
          .collect(Collectors.toMap(entry -> entry.get<caret>Key().toLowerCase(Locale.ENGLISH),
                                    entry -> entry.getValue(), (a, b) -> b));
    }
}
