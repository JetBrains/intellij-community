module consumer {
  <error descr="Text block literals are not supported at language level '9'">requires producer;</error>

  <error descr="Patterns in 'instanceof' are not supported at language level '9'">provides org.myorg.preview.FromPreview with org.myorg.preview.impl.FromPreviewImpl;</error>
}
