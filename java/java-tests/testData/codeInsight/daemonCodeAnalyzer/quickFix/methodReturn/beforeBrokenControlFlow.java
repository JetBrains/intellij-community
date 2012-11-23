// "Make 'getDrawerAppsList' return 'java.util.ArrayList<ResolveInfo>'" "true"
import java.util.*;
class Test {
  
    public static List<AppInfo> getDrawerAppsList(ArrayList<ResolveInfo> appsList) {
        for (ResolveInfo appInfo : appsList) {
            if () {

                appsList.remove(appInfo);
            }
        }

        return  <caret>appsList;

    }
}