module consumer {
  <error descr="Text block literals are not supported at language level '9'">requires producer1;</error>

  provides <error descr="Patterns in 'instanceof' are not supported at language level '9'">org.myorg.jdk.internal.javac.preview.FromPreview</error> with <error descr="Patterns in 'instanceof' are not supported at language level '9'">org.myorg.jdk.internal.javac.preview.impl.FromPreviewImpl</error>;
}
