
class Foo {
    public void foo() {
        return _status == EnhancedMemberOrderStatus.EDITING
          || _status == EnhancedMemberOrderStatus.RESUMING
          || _status == EnhancedMemberOrderStatus.OPEN;

    }
}