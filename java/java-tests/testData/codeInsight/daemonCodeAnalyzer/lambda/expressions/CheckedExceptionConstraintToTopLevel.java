package foo;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class Test {

    public void setup(OngoingStubbing<Map<String, List<String>>> stubbing) throws ReflectiveOperationException {
        stubbing.thenAnswer(inv -> to<caret>Map((Collection) inv.getArguments()[0], periodic((String) null)));
    }

    private <K, V> Map<K, V> toMap(Collection<K> keys, V v) {
        return Collections.emptyMap();
    }

    private <T> List<T> periodic(T t) throws ReflectiveOperationException {
        return null;
    }
}

abstract class OngoingStubbing<T> {
     abstract OngoingStubbing<T> thenAnswer(Answer<?> answer);
}

interface Answer<T> {
    T answer(InvocationOnMock invocation) throws Throwable;
}

interface InvocationOnMock {
    Object[] getArguments();
}