package pack;
import java.util.Map;

class BeforeRunTask {}
class RunConfiguration{}
class Key<T> {}
public abstract class Gen {
    public abstract <T extends BeforeRunTask> Map<Key<T>, BeforeRunTask> getBeforeRunTasks(RunConfiguration settings);
}

class X2 extends Gen {
    Object o = getBeforeRunTasks(null);

    public Map<Key<? extends BeforeRunTask>, BeforeRunTask> getBeforeRunTasks(RunConfiguration settings) {

        return null;
    }
}