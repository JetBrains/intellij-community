class AnonymousConstantInImplementedInterface<caret> implements AnonymousConstantInImplementedInterfaceBase {}

interface AnonymousConstantInImplementedInterfaceBase {
  AnonymousConstantInImplementedInterfaceBase DEFAULT = new AnonymousConstantInImplementedInterfaceBase() {};
}
