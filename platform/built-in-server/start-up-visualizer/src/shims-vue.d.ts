// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
declare module "*.vue" {
  import Vue from "vue"
  export default Vue
}

// https://github.com/ElemeFE/element/issues/9084
declare module "element-ui/lib/locale/lang/en" {
}

declare module "element-ui/lib/locale" {
  let v: any

  export default v
}
