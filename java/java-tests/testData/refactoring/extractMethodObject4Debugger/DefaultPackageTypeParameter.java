public class DefaultPackageTypeParameter {
    public static void main(String[] args) {
        new DefaultPackageTypeParameter().foo(new DefaultPackageTypeParameter());
    }

    private <T extends DefaultPackageTypeParameter> void foo(T value) {
        <caret>int x = 0;
    }
}
