
class MyObjBuilder {
    private NewType memberVar1;

    public NewType convertToNewType(LegacyType arg) {
        return new NewType();
    }

    public MyObjBuilder memberVar1(LegacyType arg) {
        memberVar1(convertToNewType(arg));
        return this;
    }

    public MyObjBuilder memberVar1(NewType arg) {
        this.memberVar1 = arg;
        return this;
    }

    public MyObj memberVar2() {
        return new MyObj();
    }
}

class Main {
    public static void main(String[] args) {
        LegacyType lt = new LegacyType();
        MyObj obj = MyObj.builder()
                .mem<caret>berVar1(lt)
                .memberVar2();
    }
}

class NewType {
}

class MyObj {
    public static MyObjBuilder builder() {
        return new MyObjBuilder();
    }
}

class LegacyType {}