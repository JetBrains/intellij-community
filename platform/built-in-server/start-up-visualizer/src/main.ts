// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import Vue from "vue"
import App from "./App.vue"
import {
  Main, Header, Container, Card,
  Loading, Popover,
  Table, TableColumn,
  TabPane, Tabs,
  Row, Col,
  Form, FormItem, Input, InputNumber, Checkbox, Button, Select, Cascader, Option, Link,
  Menu, MenuItem
} from "element-ui"
import router from "./router"
import {store} from "./state/state"
import locale from "element-ui/lib/locale/lang/en"

import "element-ui/lib/theme-chalk/reset.css"
import "element-ui/lib/theme-chalk/index.css"

Vue.use(Container, {locale})
Vue.use(Main, {locale})
Vue.use(Header, {locale})
Vue.use(Card, {locale})

Vue.use(Loading, {locale})
Vue.use(Popover, {locale})

Vue.use(Menu, {locale})
Vue.use(MenuItem, {locale})

Vue.use(Table, {locale})
Vue.use(TableColumn, {locale})

Vue.use(Checkbox, {locale})

Vue.use(TabPane, {locale})
Vue.use(Tabs, {locale})

Vue.use(Col, {locale})
Vue.use(Row, {locale})

Vue.use(Form, {locale})
Vue.use(FormItem, {locale})
Vue.use(Button, {locale})
Vue.use(Select, {locale})
Vue.use(Cascader, {locale})
Vue.use(Option, {locale})
Vue.use(Input, {locale})
Vue.use(InputNumber, {locale})
Vue.use(Link, {locale})

Vue.config.productionTip = false

new Vue({
  render: h => h(App),
  store,
  router,
}).$mount("#app")
