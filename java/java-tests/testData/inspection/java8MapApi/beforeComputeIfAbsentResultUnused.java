// "Replace with 'computeIfAbsent' method call" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Test {
    interface Shift {}
    void foo(Map<String, List<Shift>> dayOfWeekAndShiftTypeToShiftListMap, String key){
        List<Shift> dayOfWeekAndShiftTypeToShiftList = dayOfWeekAndShiftTypeToShiftListMap.get(key);
        if (<caret>dayOfWeekAndShiftTypeToShiftList == null) {
            dayOfWeekAndShiftTypeToShiftList = new ArrayList<>((6) / 7);
            dayOfWeekAndShiftTypeToShiftListMap.put(key, dayOfWeekAndShiftTypeToShiftList);
        }
    }
}