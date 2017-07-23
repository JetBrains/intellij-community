// "Replace with 'computeIfAbsent' method call" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Test {
    interface Shift {}
    void foo(Map<String, List<Shift>> dayOfWeekAndShiftTypeToShiftListMap, String key){
        List<Shift> dayOfWeekAndShiftTypeToShiftList = dayOfWeekAndShiftTypeToShiftListMap.computeIfAbsent(key, k -> new ArrayList<>((6) / 7));
    }
}