interface TypeA { }
interface TypeB extends TypeA { }

interface ProviderOfA {
    TypeA getObject();
}

interface ProviderOfB extends ProviderOfA {
    TypeB getObject();
}

class IntelliJPostCastScopeBug {
    void foo(ProviderOfA provider) {
        if (provider instanceof ProviderOfB) {
            provider.getObject()<caret>
        }
    }
}