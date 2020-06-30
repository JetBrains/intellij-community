module consumer {
  <error descr="Text block literals are not supported at language level '9'">requires producer;</error>

  provides <error descr="Patterns in 'instanceof' are not supported at language level '9'">org.myorg.preview.FromPreview</error> with <error descr="Patterns in 'instanceof' are not supported at language level '9'">org.myorg.preview.impl.FromPreviewImpl</error>;
}
