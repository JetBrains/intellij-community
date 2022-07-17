package x;

interface AcmClient {
    String getUser();
}
class AcmClientImpl implements AcmClient {
    @Override
    public String getUser() {
        return returnSomething();
    }
    private String returnSomething() {
        return null;
    }
}
interface SomeService {
    void doSomething();
}
class SomeServiceImpl implements SomeService {
    void invoke (Invoker r) {}
    interface Invoker {
        Object exec(Object t);
    }
    AcmClient acmClient;
    public void doSomething() {
        invoke(new Invoker() {
            @Override
            public Object exec(Object t)  {
                return acmClient.getUser();
            }
        });
    }
}
class OuterService {
    SomeService service;
    public void doOutside() {
        service.doSomething();
    }
}
