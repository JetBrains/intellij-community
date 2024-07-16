<info>module</info> myModule {
  <info>requires</info> <info descr="Not resolved until the project is fully loaded">my.unknown.mod</info>;
  
  <info>exports</info> <error descr="Package not found: my.unknown.pkg">my.unknown.pkg</error>;
}