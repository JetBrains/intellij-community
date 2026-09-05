#!/usr/bin/env node
// @bun
var __create = Object.create;
var { getPrototypeOf: __getProtoOf, defineProperty: __defProp, getOwnPropertyNames: __getOwnPropNames } = Object;
var __hasOwnProp = Object.prototype.hasOwnProperty;
function __accessProp(key) {
  return this[key];
}
var __toESMCache_node, __toESMCache_esm, __toESM = (mod, isNodeMode, target) => {
  var canCache = mod != null && typeof mod === "object";
  if (canCache) {
    var cache = isNodeMode ? __toESMCache_node ??= /* @__PURE__ */ new WeakMap : __toESMCache_esm ??= /* @__PURE__ */ new WeakMap, cached = cache.get(mod);
    if (cached)
      return cached;
  }
  target = mod != null ? __create(__getProtoOf(mod)) : {};
  let to = isNodeMode || !mod || !mod.__esModule || !__hasOwnProp.call(mod, "default") ? __defProp(target, "default", { value: mod, enumerable: !0 }) : target;
  if (mod && typeof mod === "object" || typeof mod === "function") {
    for (let key of __getOwnPropNames(mod))
      if (!__hasOwnProp.call(to, key))
        __defProp(to, key, {
          get: __accessProp.bind(mod, key),
          enumerable: !0
        });
  }
  if (canCache)
    cache.set(mod, to);
  return to;
};
var __commonJS = (cb, mod) => () => (mod || cb((mod = { exports: {} }).exports, mod), mod.exports);

// node_modules/ajv/dist/compile/codegen/code.js
var require_code = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.regexpCode = exports.getEsmExportName = exports.getProperty = exports.safeStringify = exports.stringify = exports.strConcat = exports.addCodeArg = exports.str = exports._ = exports.nil = exports._Code = exports.Name = exports.IDENTIFIER = exports._CodeOrName = void 0;

  class _CodeOrName {
  }
  exports._CodeOrName = _CodeOrName;
  exports.IDENTIFIER = /^[a-z$_][a-z$_0-9]*$/i;

  class Name extends _CodeOrName {
    constructor(s) {
      super();
      if (!exports.IDENTIFIER.test(s))
        throw Error("CodeGen: name must be a valid identifier");
      this.str = s;
    }
    toString() {
      return this.str;
    }
    emptyStr() {
      return !1;
    }
    get names() {
      return { [this.str]: 1 };
    }
  }
  exports.Name = Name;

  class _Code extends _CodeOrName {
    constructor(code) {
      super();
      this._items = typeof code === "string" ? [code] : code;
    }
    toString() {
      return this.str;
    }
    emptyStr() {
      if (this._items.length > 1)
        return !1;
      let item = this._items[0];
      return item === "" || item === '""';
    }
    get str() {
      var _a;
      return (_a = this._str) !== null && _a !== void 0 ? _a : this._str = this._items.reduce((s, c) => `${s}${c}`, "");
    }
    get names() {
      var _a;
      return (_a = this._names) !== null && _a !== void 0 ? _a : this._names = this._items.reduce((names, c) => {
        if (c instanceof Name)
          names[c.str] = (names[c.str] || 0) + 1;
        return names;
      }, {});
    }
  }
  exports._Code = _Code;
  exports.nil = new _Code("");
  function _(strs, ...args) {
    let code = [strs[0]], i = 0;
    while (i < args.length)
      addCodeArg(code, args[i]), code.push(strs[++i]);
    return new _Code(code);
  }
  exports._ = _;
  var plus = new _Code("+");
  function str(strs, ...args) {
    let expr = [safeStringify(strs[0])], i = 0;
    while (i < args.length)
      expr.push(plus), addCodeArg(expr, args[i]), expr.push(plus, safeStringify(strs[++i]));
    return optimize(expr), new _Code(expr);
  }
  exports.str = str;
  function addCodeArg(code, arg) {
    if (arg instanceof _Code)
      code.push(...arg._items);
    else if (arg instanceof Name)
      code.push(arg);
    else
      code.push(interpolate(arg));
  }
  exports.addCodeArg = addCodeArg;
  function optimize(expr) {
    let i = 1;
    while (i < expr.length - 1) {
      if (expr[i] === plus) {
        let res = mergeExprItems(expr[i - 1], expr[i + 1]);
        if (res !== void 0) {
          expr.splice(i - 1, 3, res);
          continue;
        }
        expr[i++] = "+";
      }
      i++;
    }
  }
  function mergeExprItems(a, b) {
    if (b === '""')
      return a;
    if (a === '""')
      return b;
    if (typeof a == "string") {
      if (b instanceof Name || a[a.length - 1] !== '"')
        return;
      if (typeof b != "string")
        return `${a.slice(0, -1)}${b}"`;
      if (b[0] === '"')
        return a.slice(0, -1) + b.slice(1);
      return;
    }
    if (typeof b == "string" && b[0] === '"' && !(a instanceof Name))
      return `"${a}${b.slice(1)}`;
    return;
  }
  function strConcat(c1, c2) {
    return c2.emptyStr() ? c1 : c1.emptyStr() ? c2 : str`${c1}${c2}`;
  }
  exports.strConcat = strConcat;
  function interpolate(x) {
    return typeof x == "number" || typeof x == "boolean" || x === null ? x : safeStringify(Array.isArray(x) ? x.join(",") : x);
  }
  function stringify(x) {
    return new _Code(safeStringify(x));
  }
  exports.stringify = stringify;
  function safeStringify(x) {
    return JSON.stringify(x).replace(/\u2028/g, "\\u2028").replace(/\u2029/g, "\\u2029");
  }
  exports.safeStringify = safeStringify;
  function getProperty(key) {
    return typeof key == "string" && exports.IDENTIFIER.test(key) ? new _Code(`.${key}`) : _`[${key}]`;
  }
  exports.getProperty = getProperty;
  function getEsmExportName(key) {
    if (typeof key == "string" && exports.IDENTIFIER.test(key))
      return new _Code(`${key}`);
    throw Error(`CodeGen: invalid export name: ${key}, use explicit $id name mapping`);
  }
  exports.getEsmExportName = getEsmExportName;
  function regexpCode(rx) {
    return new _Code(rx.toString());
  }
  exports.regexpCode = regexpCode;
});

// node_modules/ajv/dist/compile/codegen/scope.js
var require_scope = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.ValueScope = exports.ValueScopeName = exports.Scope = exports.varKinds = exports.UsedValueState = void 0;
  var code_1 = require_code();

  class ValueError extends Error {
    constructor(name) {
      super(`CodeGen: "code" for ${name} not defined`);
      this.value = name.value;
    }
  }
  var UsedValueState;
  (function(UsedValueState) {
    UsedValueState[UsedValueState.Started = 0] = "Started", UsedValueState[UsedValueState.Completed = 1] = "Completed";
  })(UsedValueState || (exports.UsedValueState = UsedValueState = {}));
  exports.varKinds = {
    const: new code_1.Name("const"),
    let: new code_1.Name("let"),
    var: new code_1.Name("var")
  };

  class Scope {
    constructor({ prefixes, parent } = {}) {
      this._names = {}, this._prefixes = prefixes, this._parent = parent;
    }
    toName(nameOrPrefix) {
      return nameOrPrefix instanceof code_1.Name ? nameOrPrefix : this.name(nameOrPrefix);
    }
    name(prefix) {
      return new code_1.Name(this._newName(prefix));
    }
    _newName(prefix) {
      let ng = this._names[prefix] || this._nameGroup(prefix);
      return `${prefix}${ng.index++}`;
    }
    _nameGroup(prefix) {
      var _a, _b;
      if (((_b = (_a = this._parent) === null || _a === void 0 ? void 0 : _a._prefixes) === null || _b === void 0 ? void 0 : _b.has(prefix)) || this._prefixes && !this._prefixes.has(prefix))
        throw Error(`CodeGen: prefix "${prefix}" is not allowed in this scope`);
      return this._names[prefix] = { prefix, index: 0 };
    }
  }
  exports.Scope = Scope;

  class ValueScopeName extends code_1.Name {
    constructor(prefix, nameStr) {
      super(nameStr);
      this.prefix = prefix;
    }
    setValue(value, { property, itemIndex }) {
      this.value = value, this.scopePath = code_1._`.${new code_1.Name(property)}[${itemIndex}]`;
    }
  }
  exports.ValueScopeName = ValueScopeName;
  var line = code_1._`\n`;

  class ValueScope extends Scope {
    constructor(opts) {
      super(opts);
      this._values = {}, this._scope = opts.scope, this.opts = { ...opts, _n: opts.lines ? line : code_1.nil };
    }
    get() {
      return this._scope;
    }
    name(prefix) {
      return new ValueScopeName(prefix, this._newName(prefix));
    }
    value(nameOrPrefix, value) {
      var _a;
      if (value.ref === void 0)
        throw Error("CodeGen: ref must be passed in value");
      let name = this.toName(nameOrPrefix), { prefix } = name, valueKey = (_a = value.key) !== null && _a !== void 0 ? _a : value.ref, vs = this._values[prefix];
      if (vs) {
        let _name = vs.get(valueKey);
        if (_name)
          return _name;
      } else
        vs = this._values[prefix] = /* @__PURE__ */ new Map;
      vs.set(valueKey, name);
      let s = this._scope[prefix] || (this._scope[prefix] = []), itemIndex = s.length;
      return s[itemIndex] = value.ref, name.setValue(value, { property: prefix, itemIndex }), name;
    }
    getValue(prefix, keyOrRef) {
      let vs = this._values[prefix];
      if (!vs)
        return;
      return vs.get(keyOrRef);
    }
    scopeRefs(scopeName, values = this._values) {
      return this._reduceValues(values, (name) => {
        if (name.scopePath === void 0)
          throw Error(`CodeGen: name "${name}" has no value`);
        return code_1._`${scopeName}${name.scopePath}`;
      });
    }
    scopeCode(values = this._values, usedValues, getCode) {
      return this._reduceValues(values, (name) => {
        if (name.value === void 0)
          throw Error(`CodeGen: name "${name}" has no value`);
        return name.value.code;
      }, usedValues, getCode);
    }
    _reduceValues(values, valueCode, usedValues = {}, getCode) {
      let code = code_1.nil;
      for (let prefix in values) {
        let vs = values[prefix];
        if (!vs)
          continue;
        let nameSet = usedValues[prefix] = usedValues[prefix] || /* @__PURE__ */ new Map;
        vs.forEach((name) => {
          if (nameSet.has(name))
            return;
          nameSet.set(name, UsedValueState.Started);
          let c = valueCode(name);
          if (c) {
            let def = this.opts.es5 ? exports.varKinds.var : exports.varKinds.const;
            code = code_1._`${code}${def} ${name} = ${c};${this.opts._n}`;
          } else if (c = getCode === null || getCode === void 0 ? void 0 : getCode(name))
            code = code_1._`${code}${c}${this.opts._n}`;
          else
            throw new ValueError(name);
          nameSet.set(name, UsedValueState.Completed);
        });
      }
      return code;
    }
  }
  exports.ValueScope = ValueScope;
});

// node_modules/ajv/dist/compile/codegen/index.js
var require_codegen = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.or = exports.and = exports.not = exports.CodeGen = exports.operators = exports.varKinds = exports.ValueScopeName = exports.ValueScope = exports.Scope = exports.Name = exports.regexpCode = exports.stringify = exports.getProperty = exports.nil = exports.strConcat = exports.str = exports._ = void 0;
  var code_1 = require_code(), scope_1 = require_scope(), code_2 = require_code();
  Object.defineProperty(exports, "_", { enumerable: !0, get: function() {
    return code_2._;
  } });
  Object.defineProperty(exports, "str", { enumerable: !0, get: function() {
    return code_2.str;
  } });
  Object.defineProperty(exports, "strConcat", { enumerable: !0, get: function() {
    return code_2.strConcat;
  } });
  Object.defineProperty(exports, "nil", { enumerable: !0, get: function() {
    return code_2.nil;
  } });
  Object.defineProperty(exports, "getProperty", { enumerable: !0, get: function() {
    return code_2.getProperty;
  } });
  Object.defineProperty(exports, "stringify", { enumerable: !0, get: function() {
    return code_2.stringify;
  } });
  Object.defineProperty(exports, "regexpCode", { enumerable: !0, get: function() {
    return code_2.regexpCode;
  } });
  Object.defineProperty(exports, "Name", { enumerable: !0, get: function() {
    return code_2.Name;
  } });
  var scope_2 = require_scope();
  Object.defineProperty(exports, "Scope", { enumerable: !0, get: function() {
    return scope_2.Scope;
  } });
  Object.defineProperty(exports, "ValueScope", { enumerable: !0, get: function() {
    return scope_2.ValueScope;
  } });
  Object.defineProperty(exports, "ValueScopeName", { enumerable: !0, get: function() {
    return scope_2.ValueScopeName;
  } });
  Object.defineProperty(exports, "varKinds", { enumerable: !0, get: function() {
    return scope_2.varKinds;
  } });
  exports.operators = {
    GT: new code_1._Code(">"),
    GTE: new code_1._Code(">="),
    LT: new code_1._Code("<"),
    LTE: new code_1._Code("<="),
    EQ: new code_1._Code("==="),
    NEQ: new code_1._Code("!=="),
    NOT: new code_1._Code("!"),
    OR: new code_1._Code("||"),
    AND: new code_1._Code("&&"),
    ADD: new code_1._Code("+")
  };

  class Node {
    optimizeNodes() {
      return this;
    }
    optimizeNames(_names, _constants) {
      return this;
    }
  }

  class Def extends Node {
    constructor(varKind, name, rhs) {
      super();
      this.varKind = varKind, this.name = name, this.rhs = rhs;
    }
    render({ es5, _n }) {
      let varKind = es5 ? scope_1.varKinds.var : this.varKind, rhs = this.rhs === void 0 ? "" : ` = ${this.rhs}`;
      return `${varKind} ${this.name}${rhs};` + _n;
    }
    optimizeNames(names, constants) {
      if (!names[this.name.str])
        return;
      if (this.rhs)
        this.rhs = optimizeExpr(this.rhs, names, constants);
      return this;
    }
    get names() {
      return this.rhs instanceof code_1._CodeOrName ? this.rhs.names : {};
    }
  }

  class Assign extends Node {
    constructor(lhs, rhs, sideEffects) {
      super();
      this.lhs = lhs, this.rhs = rhs, this.sideEffects = sideEffects;
    }
    render({ _n }) {
      return `${this.lhs} = ${this.rhs};` + _n;
    }
    optimizeNames(names, constants) {
      if (this.lhs instanceof code_1.Name && !names[this.lhs.str] && !this.sideEffects)
        return;
      return this.rhs = optimizeExpr(this.rhs, names, constants), this;
    }
    get names() {
      let names = this.lhs instanceof code_1.Name ? {} : { ...this.lhs.names };
      return addExprNames(names, this.rhs);
    }
  }

  class AssignOp extends Assign {
    constructor(lhs, op, rhs, sideEffects) {
      super(lhs, rhs, sideEffects);
      this.op = op;
    }
    render({ _n }) {
      return `${this.lhs} ${this.op}= ${this.rhs};` + _n;
    }
  }

  class Label extends Node {
    constructor(label) {
      super();
      this.label = label, this.names = {};
    }
    render({ _n }) {
      return `${this.label}:` + _n;
    }
  }

  class Break extends Node {
    constructor(label) {
      super();
      this.label = label, this.names = {};
    }
    render({ _n }) {
      return `break${this.label ? ` ${this.label}` : ""};` + _n;
    }
  }

  class Throw extends Node {
    constructor(error) {
      super();
      this.error = error;
    }
    render({ _n }) {
      return `throw ${this.error};` + _n;
    }
    get names() {
      return this.error.names;
    }
  }

  class AnyCode extends Node {
    constructor(code) {
      super();
      this.code = code;
    }
    render({ _n }) {
      return `${this.code};` + _n;
    }
    optimizeNodes() {
      return `${this.code}` ? this : void 0;
    }
    optimizeNames(names, constants) {
      return this.code = optimizeExpr(this.code, names, constants), this;
    }
    get names() {
      return this.code instanceof code_1._CodeOrName ? this.code.names : {};
    }
  }

  class ParentNode extends Node {
    constructor(nodes = []) {
      super();
      this.nodes = nodes;
    }
    render(opts) {
      return this.nodes.reduce((code, n) => code + n.render(opts), "");
    }
    optimizeNodes() {
      let { nodes } = this, i = nodes.length;
      while (i--) {
        let n = nodes[i].optimizeNodes();
        if (Array.isArray(n))
          nodes.splice(i, 1, ...n);
        else if (n)
          nodes[i] = n;
        else
          nodes.splice(i, 1);
      }
      return nodes.length > 0 ? this : void 0;
    }
    optimizeNames(names, constants) {
      let { nodes } = this, i = nodes.length;
      while (i--) {
        let n = nodes[i];
        if (n.optimizeNames(names, constants))
          continue;
        subtractNames(names, n.names), nodes.splice(i, 1);
      }
      return nodes.length > 0 ? this : void 0;
    }
    get names() {
      return this.nodes.reduce((names, n) => addNames(names, n.names), {});
    }
  }

  class BlockNode extends ParentNode {
    render(opts) {
      return "{" + opts._n + super.render(opts) + "}" + opts._n;
    }
  }

  class Root extends ParentNode {
  }

  class Else extends BlockNode {
  }
  Else.kind = "else";

  class If extends BlockNode {
    constructor(condition, nodes) {
      super(nodes);
      this.condition = condition;
    }
    render(opts) {
      let code = `if(${this.condition})` + super.render(opts);
      if (this.else)
        code += "else " + this.else.render(opts);
      return code;
    }
    optimizeNodes() {
      super.optimizeNodes();
      let cond = this.condition;
      if (cond === !0)
        return this.nodes;
      let e = this.else;
      if (e) {
        let ns = e.optimizeNodes();
        e = this.else = Array.isArray(ns) ? new Else(ns) : ns;
      }
      if (e) {
        if (cond === !1)
          return e instanceof If ? e : e.nodes;
        if (this.nodes.length)
          return this;
        return new If(not(cond), e instanceof If ? [e] : e.nodes);
      }
      if (cond === !1 || !this.nodes.length)
        return;
      return this;
    }
    optimizeNames(names, constants) {
      var _a;
      if (this.else = (_a = this.else) === null || _a === void 0 ? void 0 : _a.optimizeNames(names, constants), !(super.optimizeNames(names, constants) || this.else))
        return;
      return this.condition = optimizeExpr(this.condition, names, constants), this;
    }
    get names() {
      let names = super.names;
      if (addExprNames(names, this.condition), this.else)
        addNames(names, this.else.names);
      return names;
    }
  }
  If.kind = "if";

  class For extends BlockNode {
  }
  For.kind = "for";

  class ForLoop extends For {
    constructor(iteration) {
      super();
      this.iteration = iteration;
    }
    render(opts) {
      return `for(${this.iteration})` + super.render(opts);
    }
    optimizeNames(names, constants) {
      if (!super.optimizeNames(names, constants))
        return;
      return this.iteration = optimizeExpr(this.iteration, names, constants), this;
    }
    get names() {
      return addNames(super.names, this.iteration.names);
    }
  }

  class ForRange extends For {
    constructor(varKind, name, from, to) {
      super();
      this.varKind = varKind, this.name = name, this.from = from, this.to = to;
    }
    render(opts) {
      let varKind = opts.es5 ? scope_1.varKinds.var : this.varKind, { name, from, to } = this;
      return `for(${varKind} ${name}=${from}; ${name}<${to}; ${name}++)` + super.render(opts);
    }
    get names() {
      let names = addExprNames(super.names, this.from);
      return addExprNames(names, this.to);
    }
  }

  class ForIter extends For {
    constructor(loop, varKind, name, iterable) {
      super();
      this.loop = loop, this.varKind = varKind, this.name = name, this.iterable = iterable;
    }
    render(opts) {
      return `for(${this.varKind} ${this.name} ${this.loop} ${this.iterable})` + super.render(opts);
    }
    optimizeNames(names, constants) {
      if (!super.optimizeNames(names, constants))
        return;
      return this.iterable = optimizeExpr(this.iterable, names, constants), this;
    }
    get names() {
      return addNames(super.names, this.iterable.names);
    }
  }

  class Func extends BlockNode {
    constructor(name, args, async) {
      super();
      this.name = name, this.args = args, this.async = async;
    }
    render(opts) {
      return `${this.async ? "async " : ""}function ${this.name}(${this.args})` + super.render(opts);
    }
  }
  Func.kind = "func";

  class Return extends ParentNode {
    render(opts) {
      return "return " + super.render(opts);
    }
  }
  Return.kind = "return";

  class Try extends BlockNode {
    render(opts) {
      let code = "try" + super.render(opts);
      if (this.catch)
        code += this.catch.render(opts);
      if (this.finally)
        code += this.finally.render(opts);
      return code;
    }
    optimizeNodes() {
      var _a, _b;
      return super.optimizeNodes(), (_a = this.catch) === null || _a === void 0 || _a.optimizeNodes(), (_b = this.finally) === null || _b === void 0 || _b.optimizeNodes(), this;
    }
    optimizeNames(names, constants) {
      var _a, _b;
      return super.optimizeNames(names, constants), (_a = this.catch) === null || _a === void 0 || _a.optimizeNames(names, constants), (_b = this.finally) === null || _b === void 0 || _b.optimizeNames(names, constants), this;
    }
    get names() {
      let names = super.names;
      if (this.catch)
        addNames(names, this.catch.names);
      if (this.finally)
        addNames(names, this.finally.names);
      return names;
    }
  }

  class Catch extends BlockNode {
    constructor(error) {
      super();
      this.error = error;
    }
    render(opts) {
      return `catch(${this.error})` + super.render(opts);
    }
  }
  Catch.kind = "catch";

  class Finally extends BlockNode {
    render(opts) {
      return "finally" + super.render(opts);
    }
  }
  Finally.kind = "finally";

  class CodeGen {
    constructor(extScope, opts = {}) {
      this._values = {}, this._blockStarts = [], this._constants = {}, this.opts = { ...opts, _n: opts.lines ? `
` : "" }, this._extScope = extScope, this._scope = new scope_1.Scope({ parent: extScope }), this._nodes = [new Root];
    }
    toString() {
      return this._root.render(this.opts);
    }
    name(prefix) {
      return this._scope.name(prefix);
    }
    scopeName(prefix) {
      return this._extScope.name(prefix);
    }
    scopeValue(prefixOrName, value) {
      let name = this._extScope.value(prefixOrName, value);
      return (this._values[name.prefix] || (this._values[name.prefix] = /* @__PURE__ */ new Set)).add(name), name;
    }
    getScopeValue(prefix, keyOrRef) {
      return this._extScope.getValue(prefix, keyOrRef);
    }
    scopeRefs(scopeName) {
      return this._extScope.scopeRefs(scopeName, this._values);
    }
    scopeCode() {
      return this._extScope.scopeCode(this._values);
    }
    _def(varKind, nameOrPrefix, rhs, constant) {
      let name = this._scope.toName(nameOrPrefix);
      if (rhs !== void 0 && constant)
        this._constants[name.str] = rhs;
      return this._leafNode(new Def(varKind, name, rhs)), name;
    }
    const(nameOrPrefix, rhs, _constant) {
      return this._def(scope_1.varKinds.const, nameOrPrefix, rhs, _constant);
    }
    let(nameOrPrefix, rhs, _constant) {
      return this._def(scope_1.varKinds.let, nameOrPrefix, rhs, _constant);
    }
    var(nameOrPrefix, rhs, _constant) {
      return this._def(scope_1.varKinds.var, nameOrPrefix, rhs, _constant);
    }
    assign(lhs, rhs, sideEffects) {
      return this._leafNode(new Assign(lhs, rhs, sideEffects));
    }
    add(lhs, rhs) {
      return this._leafNode(new AssignOp(lhs, exports.operators.ADD, rhs));
    }
    code(c) {
      if (typeof c == "function")
        c();
      else if (c !== code_1.nil)
        this._leafNode(new AnyCode(c));
      return this;
    }
    object(...keyValues) {
      let code = ["{"];
      for (let [key, value] of keyValues) {
        if (code.length > 1)
          code.push(",");
        if (code.push(key), key !== value || this.opts.es5)
          code.push(":"), code_1.addCodeArg(code, value);
      }
      return code.push("}"), new code_1._Code(code);
    }
    if(condition, thenBody, elseBody) {
      if (this._blockNode(new If(condition)), thenBody && elseBody)
        this.code(thenBody).else().code(elseBody).endIf();
      else if (thenBody)
        this.code(thenBody).endIf();
      else if (elseBody)
        throw Error('CodeGen: "else" body without "then" body');
      return this;
    }
    elseIf(condition) {
      return this._elseNode(new If(condition));
    }
    else() {
      return this._elseNode(new Else);
    }
    endIf() {
      return this._endBlockNode(If, Else);
    }
    _for(node, forBody) {
      if (this._blockNode(node), forBody)
        this.code(forBody).endFor();
      return this;
    }
    for(iteration, forBody) {
      return this._for(new ForLoop(iteration), forBody);
    }
    forRange(nameOrPrefix, from, to, forBody, varKind = this.opts.es5 ? scope_1.varKinds.var : scope_1.varKinds.let) {
      let name = this._scope.toName(nameOrPrefix);
      return this._for(new ForRange(varKind, name, from, to), () => forBody(name));
    }
    forOf(nameOrPrefix, iterable, forBody, varKind = scope_1.varKinds.const) {
      let name = this._scope.toName(nameOrPrefix);
      if (this.opts.es5) {
        let arr = iterable instanceof code_1.Name ? iterable : this.var("_arr", iterable);
        return this.forRange("_i", 0, code_1._`${arr}.length`, (i) => {
          this.var(name, code_1._`${arr}[${i}]`), forBody(name);
        });
      }
      return this._for(new ForIter("of", varKind, name, iterable), () => forBody(name));
    }
    forIn(nameOrPrefix, obj, forBody, varKind = this.opts.es5 ? scope_1.varKinds.var : scope_1.varKinds.const) {
      if (this.opts.ownProperties)
        return this.forOf(nameOrPrefix, code_1._`Object.keys(${obj})`, forBody);
      let name = this._scope.toName(nameOrPrefix);
      return this._for(new ForIter("in", varKind, name, obj), () => forBody(name));
    }
    endFor() {
      return this._endBlockNode(For);
    }
    label(label) {
      return this._leafNode(new Label(label));
    }
    break(label) {
      return this._leafNode(new Break(label));
    }
    return(value) {
      let node = new Return;
      if (this._blockNode(node), this.code(value), node.nodes.length !== 1)
        throw Error('CodeGen: "return" should have one node');
      return this._endBlockNode(Return);
    }
    try(tryBody, catchCode, finallyCode) {
      if (!catchCode && !finallyCode)
        throw Error('CodeGen: "try" without "catch" and "finally"');
      let node = new Try;
      if (this._blockNode(node), this.code(tryBody), catchCode) {
        let error = this.name("e");
        this._currNode = node.catch = new Catch(error), catchCode(error);
      }
      if (finallyCode)
        this._currNode = node.finally = new Finally, this.code(finallyCode);
      return this._endBlockNode(Catch, Finally);
    }
    throw(error) {
      return this._leafNode(new Throw(error));
    }
    block(body, nodeCount) {
      if (this._blockStarts.push(this._nodes.length), body)
        this.code(body).endBlock(nodeCount);
      return this;
    }
    endBlock(nodeCount) {
      let len = this._blockStarts.pop();
      if (len === void 0)
        throw Error("CodeGen: not in self-balancing block");
      let toClose = this._nodes.length - len;
      if (toClose < 0 || nodeCount !== void 0 && toClose !== nodeCount)
        throw Error(`CodeGen: wrong number of nodes: ${toClose} vs ${nodeCount} expected`);
      return this._nodes.length = len, this;
    }
    func(name, args = code_1.nil, async, funcBody) {
      if (this._blockNode(new Func(name, args, async)), funcBody)
        this.code(funcBody).endFunc();
      return this;
    }
    endFunc() {
      return this._endBlockNode(Func);
    }
    optimize(n = 1) {
      while (n-- > 0)
        this._root.optimizeNodes(), this._root.optimizeNames(this._root.names, this._constants);
    }
    _leafNode(node) {
      return this._currNode.nodes.push(node), this;
    }
    _blockNode(node) {
      this._currNode.nodes.push(node), this._nodes.push(node);
    }
    _endBlockNode(N1, N2) {
      let n = this._currNode;
      if (n instanceof N1 || N2 && n instanceof N2)
        return this._nodes.pop(), this;
      throw Error(`CodeGen: not in block "${N2 ? `${N1.kind}/${N2.kind}` : N1.kind}"`);
    }
    _elseNode(node) {
      let n = this._currNode;
      if (!(n instanceof If))
        throw Error('CodeGen: "else" without "if"');
      return this._currNode = n.else = node, this;
    }
    get _root() {
      return this._nodes[0];
    }
    get _currNode() {
      let ns = this._nodes;
      return ns[ns.length - 1];
    }
    set _currNode(node) {
      let ns = this._nodes;
      ns[ns.length - 1] = node;
    }
  }
  exports.CodeGen = CodeGen;
  function addNames(names, from) {
    for (let n in from)
      names[n] = (names[n] || 0) + (from[n] || 0);
    return names;
  }
  function addExprNames(names, from) {
    return from instanceof code_1._CodeOrName ? addNames(names, from.names) : names;
  }
  function optimizeExpr(expr, names, constants) {
    if (expr instanceof code_1.Name)
      return replaceName(expr);
    if (!canOptimize(expr))
      return expr;
    return new code_1._Code(expr._items.reduce((items, c) => {
      if (c instanceof code_1.Name)
        c = replaceName(c);
      if (c instanceof code_1._Code)
        items.push(...c._items);
      else
        items.push(c);
      return items;
    }, []));
    function replaceName(n) {
      let c = constants[n.str];
      if (c === void 0 || names[n.str] !== 1)
        return n;
      return delete names[n.str], c;
    }
    function canOptimize(e) {
      return e instanceof code_1._Code && e._items.some((c) => c instanceof code_1.Name && names[c.str] === 1 && constants[c.str] !== void 0);
    }
  }
  function subtractNames(names, from) {
    for (let n in from)
      names[n] = (names[n] || 0) - (from[n] || 0);
  }
  function not(x) {
    return typeof x == "boolean" || typeof x == "number" || x === null ? !x : code_1._`!${par(x)}`;
  }
  exports.not = not;
  var andCode = mappend(exports.operators.AND);
  function and(...args) {
    return args.reduce(andCode);
  }
  exports.and = and;
  var orCode = mappend(exports.operators.OR);
  function or(...args) {
    return args.reduce(orCode);
  }
  exports.or = or;
  function mappend(op) {
    return (x, y) => x === code_1.nil ? y : y === code_1.nil ? x : code_1._`${par(x)} ${op} ${par(y)}`;
  }
  function par(x) {
    return x instanceof code_1.Name ? x : code_1._`(${x})`;
  }
});

// node_modules/ajv/dist/compile/util.js
var require_util = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.checkStrictMode = exports.getErrorPath = exports.Type = exports.useFunc = exports.setEvaluated = exports.evaluatedPropsToName = exports.mergeEvaluated = exports.eachItem = exports.unescapeJsonPointer = exports.escapeJsonPointer = exports.escapeFragment = exports.unescapeFragment = exports.schemaRefOrVal = exports.schemaHasRulesButRef = exports.schemaHasRules = exports.checkUnknownRules = exports.alwaysValidSchema = exports.toHash = void 0;
  var codegen_1 = require_codegen(), code_1 = require_code();
  function toHash(arr) {
    let hash = {};
    for (let item of arr)
      hash[item] = !0;
    return hash;
  }
  exports.toHash = toHash;
  function alwaysValidSchema(it, schema) {
    if (typeof schema == "boolean")
      return schema;
    if (Object.keys(schema).length === 0)
      return !0;
    return checkUnknownRules(it, schema), !schemaHasRules(schema, it.self.RULES.all);
  }
  exports.alwaysValidSchema = alwaysValidSchema;
  function checkUnknownRules(it, schema = it.schema) {
    let { opts, self } = it;
    if (!opts.strictSchema)
      return;
    if (typeof schema === "boolean")
      return;
    let rules = self.RULES.keywords;
    for (let key in schema)
      if (!rules[key])
        checkStrictMode(it, `unknown keyword: "${key}"`);
  }
  exports.checkUnknownRules = checkUnknownRules;
  function schemaHasRules(schema, rules) {
    if (typeof schema == "boolean")
      return !schema;
    for (let key in schema)
      if (rules[key])
        return !0;
    return !1;
  }
  exports.schemaHasRules = schemaHasRules;
  function schemaHasRulesButRef(schema, RULES) {
    if (typeof schema == "boolean")
      return !schema;
    for (let key in schema)
      if (key !== "$ref" && RULES.all[key])
        return !0;
    return !1;
  }
  exports.schemaHasRulesButRef = schemaHasRulesButRef;
  function schemaRefOrVal({ topSchemaRef, schemaPath }, schema, keyword, $data) {
    if (!$data) {
      if (typeof schema == "number" || typeof schema == "boolean")
        return schema;
      if (typeof schema == "string")
        return codegen_1._`${schema}`;
    }
    return codegen_1._`${topSchemaRef}${schemaPath}${codegen_1.getProperty(keyword)}`;
  }
  exports.schemaRefOrVal = schemaRefOrVal;
  function unescapeFragment(str) {
    return unescapeJsonPointer(decodeURIComponent(str));
  }
  exports.unescapeFragment = unescapeFragment;
  function escapeFragment(str) {
    return encodeURIComponent(escapeJsonPointer(str));
  }
  exports.escapeFragment = escapeFragment;
  function escapeJsonPointer(str) {
    if (typeof str == "number")
      return `${str}`;
    return str.replace(/~/g, "~0").replace(/\//g, "~1");
  }
  exports.escapeJsonPointer = escapeJsonPointer;
  function unescapeJsonPointer(str) {
    return str.replace(/~1/g, "/").replace(/~0/g, "~");
  }
  exports.unescapeJsonPointer = unescapeJsonPointer;
  function eachItem(xs, f) {
    if (Array.isArray(xs))
      for (let x of xs)
        f(x);
    else
      f(xs);
  }
  exports.eachItem = eachItem;
  function makeMergeEvaluated({ mergeNames, mergeToName, mergeValues, resultToName }) {
    return (gen, from, to, toName) => {
      let res = to === void 0 ? from : to instanceof codegen_1.Name ? (from instanceof codegen_1.Name ? mergeNames(gen, from, to) : mergeToName(gen, from, to), to) : from instanceof codegen_1.Name ? (mergeToName(gen, to, from), from) : mergeValues(from, to);
      return toName === codegen_1.Name && !(res instanceof codegen_1.Name) ? resultToName(gen, res) : res;
    };
  }
  exports.mergeEvaluated = {
    props: makeMergeEvaluated({
      mergeNames: (gen, from, to) => gen.if(codegen_1._`${to} !== true && ${from} !== undefined`, () => {
        gen.if(codegen_1._`${from} === true`, () => gen.assign(to, !0), () => gen.assign(to, codegen_1._`${to} || {}`).code(codegen_1._`Object.assign(${to}, ${from})`));
      }),
      mergeToName: (gen, from, to) => gen.if(codegen_1._`${to} !== true`, () => {
        if (from === !0)
          gen.assign(to, !0);
        else
          gen.assign(to, codegen_1._`${to} || {}`), setEvaluated(gen, to, from);
      }),
      mergeValues: (from, to) => from === !0 ? !0 : { ...from, ...to },
      resultToName: evaluatedPropsToName
    }),
    items: makeMergeEvaluated({
      mergeNames: (gen, from, to) => gen.if(codegen_1._`${to} !== true && ${from} !== undefined`, () => gen.assign(to, codegen_1._`${from} === true ? true : ${to} > ${from} ? ${to} : ${from}`)),
      mergeToName: (gen, from, to) => gen.if(codegen_1._`${to} !== true`, () => gen.assign(to, from === !0 ? !0 : codegen_1._`${to} > ${from} ? ${to} : ${from}`)),
      mergeValues: (from, to) => from === !0 ? !0 : Math.max(from, to),
      resultToName: (gen, items) => gen.var("items", items)
    })
  };
  function evaluatedPropsToName(gen, ps) {
    if (ps === !0)
      return gen.var("props", !0);
    let props = gen.var("props", codegen_1._`{}`);
    if (ps !== void 0)
      setEvaluated(gen, props, ps);
    return props;
  }
  exports.evaluatedPropsToName = evaluatedPropsToName;
  function setEvaluated(gen, props, ps) {
    Object.keys(ps).forEach((p) => gen.assign(codegen_1._`${props}${codegen_1.getProperty(p)}`, !0));
  }
  exports.setEvaluated = setEvaluated;
  var snippets = {};
  function useFunc(gen, f) {
    return gen.scopeValue("func", {
      ref: f,
      code: snippets[f.code] || (snippets[f.code] = new code_1._Code(f.code))
    });
  }
  exports.useFunc = useFunc;
  var Type;
  (function(Type) {
    Type[Type.Num = 0] = "Num", Type[Type.Str = 1] = "Str";
  })(Type || (exports.Type = Type = {}));
  function getErrorPath(dataProp, dataPropType, jsPropertySyntax) {
    if (dataProp instanceof codegen_1.Name) {
      let isNumber = dataPropType === Type.Num;
      return jsPropertySyntax ? isNumber ? codegen_1._`"[" + ${dataProp} + "]"` : codegen_1._`"['" + ${dataProp} + "']"` : isNumber ? codegen_1._`"/" + ${dataProp}` : codegen_1._`"/" + ${dataProp}.replace(/~/g, "~0").replace(/\\//g, "~1")`;
    }
    return jsPropertySyntax ? codegen_1.getProperty(dataProp).toString() : "/" + escapeJsonPointer(dataProp);
  }
  exports.getErrorPath = getErrorPath;
  function checkStrictMode(it, msg, mode = it.opts.strictSchema) {
    if (!mode)
      return;
    if (msg = `strict mode: ${msg}`, mode === !0)
      throw Error(msg);
    it.self.logger.warn(msg);
  }
  exports.checkStrictMode = checkStrictMode;
});

// node_modules/ajv/dist/compile/names.js
var require_names = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var codegen_1 = require_codegen(), names = {
    data: new codegen_1.Name("data"),
    valCxt: new codegen_1.Name("valCxt"),
    instancePath: new codegen_1.Name("instancePath"),
    parentData: new codegen_1.Name("parentData"),
    parentDataProperty: new codegen_1.Name("parentDataProperty"),
    rootData: new codegen_1.Name("rootData"),
    dynamicAnchors: new codegen_1.Name("dynamicAnchors"),
    vErrors: new codegen_1.Name("vErrors"),
    errors: new codegen_1.Name("errors"),
    this: new codegen_1.Name("this"),
    self: new codegen_1.Name("self"),
    scope: new codegen_1.Name("scope"),
    json: new codegen_1.Name("json"),
    jsonPos: new codegen_1.Name("jsonPos"),
    jsonLen: new codegen_1.Name("jsonLen"),
    jsonPart: new codegen_1.Name("jsonPart")
  };
  exports.default = names;
});

// node_modules/ajv/dist/compile/errors.js
var require_errors = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.extendErrors = exports.resetErrorsCount = exports.reportExtraError = exports.reportError = exports.keyword$DataError = exports.keywordError = void 0;
  var codegen_1 = require_codegen(), util_1 = require_util(), names_1 = require_names();
  exports.keywordError = {
    message: ({ keyword }) => codegen_1.str`must pass "${keyword}" keyword validation`
  };
  exports.keyword$DataError = {
    message: ({ keyword, schemaType }) => schemaType ? codegen_1.str`"${keyword}" keyword must be ${schemaType} ($data)` : codegen_1.str`"${keyword}" keyword is invalid ($data)`
  };
  function reportError(cxt, error = exports.keywordError, errorPaths, overrideAllErrors) {
    let { it } = cxt, { gen, compositeRule, allErrors } = it, errObj = errorObjectCode(cxt, error, errorPaths);
    if (overrideAllErrors !== null && overrideAllErrors !== void 0 ? overrideAllErrors : compositeRule || allErrors)
      addError(gen, errObj);
    else
      returnErrors(it, codegen_1._`[${errObj}]`);
  }
  exports.reportError = reportError;
  function reportExtraError(cxt, error = exports.keywordError, errorPaths) {
    let { it } = cxt, { gen, compositeRule, allErrors } = it, errObj = errorObjectCode(cxt, error, errorPaths);
    if (addError(gen, errObj), !(compositeRule || allErrors))
      returnErrors(it, names_1.default.vErrors);
  }
  exports.reportExtraError = reportExtraError;
  function resetErrorsCount(gen, errsCount) {
    gen.assign(names_1.default.errors, errsCount), gen.if(codegen_1._`${names_1.default.vErrors} !== null`, () => gen.if(errsCount, () => gen.assign(codegen_1._`${names_1.default.vErrors}.length`, errsCount), () => gen.assign(names_1.default.vErrors, null)));
  }
  exports.resetErrorsCount = resetErrorsCount;
  function extendErrors({ gen, keyword, schemaValue, data, errsCount, it }) {
    if (errsCount === void 0)
      throw Error("ajv implementation error");
    let err = gen.name("err");
    gen.forRange("i", errsCount, names_1.default.errors, (i) => {
      if (gen.const(err, codegen_1._`${names_1.default.vErrors}[${i}]`), gen.if(codegen_1._`${err}.instancePath === undefined`, () => gen.assign(codegen_1._`${err}.instancePath`, codegen_1.strConcat(names_1.default.instancePath, it.errorPath))), gen.assign(codegen_1._`${err}.schemaPath`, codegen_1.str`${it.errSchemaPath}/${keyword}`), it.opts.verbose)
        gen.assign(codegen_1._`${err}.schema`, schemaValue), gen.assign(codegen_1._`${err}.data`, data);
    });
  }
  exports.extendErrors = extendErrors;
  function addError(gen, errObj) {
    let err = gen.const("err", errObj);
    gen.if(codegen_1._`${names_1.default.vErrors} === null`, () => gen.assign(names_1.default.vErrors, codegen_1._`[${err}]`), codegen_1._`${names_1.default.vErrors}.push(${err})`), gen.code(codegen_1._`${names_1.default.errors}++`);
  }
  function returnErrors(it, errs) {
    let { gen, validateName, schemaEnv } = it;
    if (schemaEnv.$async)
      gen.throw(codegen_1._`new ${it.ValidationError}(${errs})`);
    else
      gen.assign(codegen_1._`${validateName}.errors`, errs), gen.return(!1);
  }
  var E = {
    keyword: new codegen_1.Name("keyword"),
    schemaPath: new codegen_1.Name("schemaPath"),
    params: new codegen_1.Name("params"),
    propertyName: new codegen_1.Name("propertyName"),
    message: new codegen_1.Name("message"),
    schema: new codegen_1.Name("schema"),
    parentSchema: new codegen_1.Name("parentSchema")
  };
  function errorObjectCode(cxt, error, errorPaths) {
    let { createErrors } = cxt.it;
    if (createErrors === !1)
      return codegen_1._`{}`;
    return errorObject(cxt, error, errorPaths);
  }
  function errorObject(cxt, error, errorPaths = {}) {
    let { gen, it } = cxt, keyValues = [
      errorInstancePath(it, errorPaths),
      errorSchemaPath(cxt, errorPaths)
    ];
    return extraErrorProps(cxt, error, keyValues), gen.object(...keyValues);
  }
  function errorInstancePath({ errorPath }, { instancePath }) {
    let instPath = instancePath ? codegen_1.str`${errorPath}${util_1.getErrorPath(instancePath, util_1.Type.Str)}` : errorPath;
    return [names_1.default.instancePath, codegen_1.strConcat(names_1.default.instancePath, instPath)];
  }
  function errorSchemaPath({ keyword, it: { errSchemaPath } }, { schemaPath, parentSchema }) {
    let schPath = parentSchema ? errSchemaPath : codegen_1.str`${errSchemaPath}/${keyword}`;
    if (schemaPath)
      schPath = codegen_1.str`${schPath}${util_1.getErrorPath(schemaPath, util_1.Type.Str)}`;
    return [E.schemaPath, schPath];
  }
  function extraErrorProps(cxt, { params, message }, keyValues) {
    let { keyword, data, schemaValue, it } = cxt, { opts, propertyName, topSchemaRef, schemaPath } = it;
    if (keyValues.push([E.keyword, keyword], [E.params, typeof params == "function" ? params(cxt) : params || codegen_1._`{}`]), opts.messages)
      keyValues.push([E.message, typeof message == "function" ? message(cxt) : message]);
    if (opts.verbose)
      keyValues.push([E.schema, schemaValue], [E.parentSchema, codegen_1._`${topSchemaRef}${schemaPath}`], [names_1.default.data, data]);
    if (propertyName)
      keyValues.push([E.propertyName, propertyName]);
  }
});

// node_modules/ajv/dist/compile/validate/boolSchema.js
var require_boolSchema = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.boolOrEmptySchema = exports.topBoolOrEmptySchema = void 0;
  var errors_1 = require_errors(), codegen_1 = require_codegen(), names_1 = require_names(), boolError = {
    message: "boolean schema is false"
  };
  function topBoolOrEmptySchema(it) {
    let { gen, schema, validateName } = it;
    if (schema === !1)
      falseSchemaError(it, !1);
    else if (typeof schema == "object" && schema.$async === !0)
      gen.return(names_1.default.data);
    else
      gen.assign(codegen_1._`${validateName}.errors`, null), gen.return(!0);
  }
  exports.topBoolOrEmptySchema = topBoolOrEmptySchema;
  function boolOrEmptySchema(it, valid) {
    let { gen, schema } = it;
    if (schema === !1)
      gen.var(valid, !1), falseSchemaError(it);
    else
      gen.var(valid, !0);
  }
  exports.boolOrEmptySchema = boolOrEmptySchema;
  function falseSchemaError(it, overrideAllErrors) {
    let { gen, data } = it;
    errors_1.reportError({
      gen,
      keyword: "false schema",
      data,
      schema: !1,
      schemaCode: !1,
      schemaValue: !1,
      params: {},
      it
    }, boolError, void 0, overrideAllErrors);
  }
});

// node_modules/ajv/dist/compile/rules.js
var require_rules = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.getRules = exports.isJSONType = void 0;
  var _jsonTypes = ["string", "number", "integer", "boolean", "null", "object", "array"], jsonTypes = new Set(_jsonTypes);
  function isJSONType(x) {
    return typeof x == "string" && jsonTypes.has(x);
  }
  exports.isJSONType = isJSONType;
  function getRules() {
    let groups = {
      number: { type: "number", rules: [] },
      string: { type: "string", rules: [] },
      array: { type: "array", rules: [] },
      object: { type: "object", rules: [] }
    };
    return {
      types: { ...groups, integer: !0, boolean: !0, null: !0 },
      rules: [{ rules: [] }, groups.number, groups.string, groups.array, groups.object],
      post: { rules: [] },
      all: {},
      keywords: {}
    };
  }
  exports.getRules = getRules;
});

// node_modules/ajv/dist/compile/validate/applicability.js
var require_applicability = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.shouldUseRule = exports.shouldUseGroup = exports.schemaHasRulesForType = void 0;
  function schemaHasRulesForType({ schema, self }, type) {
    let group = self.RULES.types[type];
    return group && group !== !0 && shouldUseGroup(schema, group);
  }
  exports.schemaHasRulesForType = schemaHasRulesForType;
  function shouldUseGroup(schema, group) {
    return group.rules.some((rule) => shouldUseRule(schema, rule));
  }
  exports.shouldUseGroup = shouldUseGroup;
  function shouldUseRule(schema, rule) {
    var _a;
    return schema[rule.keyword] !== void 0 || ((_a = rule.definition.implements) === null || _a === void 0 ? void 0 : _a.some((kwd) => schema[kwd] !== void 0));
  }
  exports.shouldUseRule = shouldUseRule;
});

// node_modules/ajv/dist/compile/validate/dataType.js
var require_dataType = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.reportTypeError = exports.checkDataTypes = exports.checkDataType = exports.coerceAndCheckDataType = exports.getJSONTypes = exports.getSchemaTypes = exports.DataType = void 0;
  var rules_1 = require_rules(), applicability_1 = require_applicability(), errors_1 = require_errors(), codegen_1 = require_codegen(), util_1 = require_util(), DataType;
  (function(DataType) {
    DataType[DataType.Correct = 0] = "Correct", DataType[DataType.Wrong = 1] = "Wrong";
  })(DataType || (exports.DataType = DataType = {}));
  function getSchemaTypes(schema) {
    let types = getJSONTypes(schema.type);
    if (types.includes("null")) {
      if (schema.nullable === !1)
        throw Error("type: null contradicts nullable: false");
    } else {
      if (!types.length && schema.nullable !== void 0)
        throw Error('"nullable" cannot be used without "type"');
      if (schema.nullable === !0)
        types.push("null");
    }
    return types;
  }
  exports.getSchemaTypes = getSchemaTypes;
  function getJSONTypes(ts) {
    let types = Array.isArray(ts) ? ts : ts ? [ts] : [];
    if (types.every(rules_1.isJSONType))
      return types;
    throw Error("type must be JSONType or JSONType[]: " + types.join(","));
  }
  exports.getJSONTypes = getJSONTypes;
  function coerceAndCheckDataType(it, types) {
    let { gen, data, opts } = it, coerceTo = coerceToTypes(types, opts.coerceTypes), checkTypes = types.length > 0 && !(coerceTo.length === 0 && types.length === 1 && applicability_1.schemaHasRulesForType(it, types[0]));
    if (checkTypes) {
      let wrongType = checkDataTypes(types, data, opts.strictNumbers, DataType.Wrong);
      gen.if(wrongType, () => {
        if (coerceTo.length)
          coerceData(it, types, coerceTo);
        else
          reportTypeError(it);
      });
    }
    return checkTypes;
  }
  exports.coerceAndCheckDataType = coerceAndCheckDataType;
  var COERCIBLE = /* @__PURE__ */ new Set(["string", "number", "integer", "boolean", "null"]);
  function coerceToTypes(types, coerceTypes) {
    return coerceTypes ? types.filter((t) => COERCIBLE.has(t) || coerceTypes === "array" && t === "array") : [];
  }
  function coerceData(it, types, coerceTo) {
    let { gen, data, opts } = it, dataType = gen.let("dataType", codegen_1._`typeof ${data}`), coerced = gen.let("coerced", codegen_1._`undefined`);
    if (opts.coerceTypes === "array")
      gen.if(codegen_1._`${dataType} == 'object' && Array.isArray(${data}) && ${data}.length == 1`, () => gen.assign(data, codegen_1._`${data}[0]`).assign(dataType, codegen_1._`typeof ${data}`).if(checkDataTypes(types, data, opts.strictNumbers), () => gen.assign(coerced, data)));
    gen.if(codegen_1._`${coerced} !== undefined`);
    for (let t of coerceTo)
      if (COERCIBLE.has(t) || t === "array" && opts.coerceTypes === "array")
        coerceSpecificType(t);
    gen.else(), reportTypeError(it), gen.endIf(), gen.if(codegen_1._`${coerced} !== undefined`, () => {
      gen.assign(data, coerced), assignParentData(it, coerced);
    });
    function coerceSpecificType(t) {
      switch (t) {
        case "string":
          gen.elseIf(codegen_1._`${dataType} == "number" || ${dataType} == "boolean"`).assign(coerced, codegen_1._`"" + ${data}`).elseIf(codegen_1._`${data} === null`).assign(coerced, codegen_1._`""`);
          return;
        case "number":
          gen.elseIf(codegen_1._`${dataType} == "boolean" || ${data} === null
              || (${dataType} == "string" && ${data} && ${data} == +${data})`).assign(coerced, codegen_1._`+${data}`);
          return;
        case "integer":
          gen.elseIf(codegen_1._`${dataType} === "boolean" || ${data} === null
              || (${dataType} === "string" && ${data} && ${data} == +${data} && !(${data} % 1))`).assign(coerced, codegen_1._`+${data}`);
          return;
        case "boolean":
          gen.elseIf(codegen_1._`${data} === "false" || ${data} === 0 || ${data} === null`).assign(coerced, !1).elseIf(codegen_1._`${data} === "true" || ${data} === 1`).assign(coerced, !0);
          return;
        case "null":
          gen.elseIf(codegen_1._`${data} === "" || ${data} === 0 || ${data} === false`), gen.assign(coerced, null);
          return;
        case "array":
          gen.elseIf(codegen_1._`${dataType} === "string" || ${dataType} === "number"
              || ${dataType} === "boolean" || ${data} === null`).assign(coerced, codegen_1._`[${data}]`);
      }
    }
  }
  function assignParentData({ gen, parentData, parentDataProperty }, expr) {
    gen.if(codegen_1._`${parentData} !== undefined`, () => gen.assign(codegen_1._`${parentData}[${parentDataProperty}]`, expr));
  }
  function checkDataType(dataType, data, strictNums, correct = DataType.Correct) {
    let EQ = correct === DataType.Correct ? codegen_1.operators.EQ : codegen_1.operators.NEQ, cond;
    switch (dataType) {
      case "null":
        return codegen_1._`${data} ${EQ} null`;
      case "array":
        cond = codegen_1._`Array.isArray(${data})`;
        break;
      case "object":
        cond = codegen_1._`${data} && typeof ${data} == "object" && !Array.isArray(${data})`;
        break;
      case "integer":
        cond = numCond(codegen_1._`!(${data} % 1) && !isNaN(${data})`);
        break;
      case "number":
        cond = numCond();
        break;
      default:
        return codegen_1._`typeof ${data} ${EQ} ${dataType}`;
    }
    return correct === DataType.Correct ? cond : codegen_1.not(cond);
    function numCond(_cond = codegen_1.nil) {
      return codegen_1.and(codegen_1._`typeof ${data} == "number"`, _cond, strictNums ? codegen_1._`isFinite(${data})` : codegen_1.nil);
    }
  }
  exports.checkDataType = checkDataType;
  function checkDataTypes(dataTypes, data, strictNums, correct) {
    if (dataTypes.length === 1)
      return checkDataType(dataTypes[0], data, strictNums, correct);
    let cond, types = util_1.toHash(dataTypes);
    if (types.array && types.object) {
      let notObj = codegen_1._`typeof ${data} != "object"`;
      cond = types.null ? notObj : codegen_1._`!${data} || ${notObj}`, delete types.null, delete types.array, delete types.object;
    } else
      cond = codegen_1.nil;
    if (types.number)
      delete types.integer;
    for (let t in types)
      cond = codegen_1.and(cond, checkDataType(t, data, strictNums, correct));
    return cond;
  }
  exports.checkDataTypes = checkDataTypes;
  var typeError = {
    message: ({ schema }) => `must be ${schema}`,
    params: ({ schema, schemaValue }) => typeof schema == "string" ? codegen_1._`{type: ${schema}}` : codegen_1._`{type: ${schemaValue}}`
  };
  function reportTypeError(it) {
    let cxt = getTypeErrorContext(it);
    errors_1.reportError(cxt, typeError);
  }
  exports.reportTypeError = reportTypeError;
  function getTypeErrorContext(it) {
    let { gen, data, schema } = it, schemaCode = util_1.schemaRefOrVal(it, schema, "type");
    return {
      gen,
      keyword: "type",
      data,
      schema: schema.type,
      schemaCode,
      schemaValue: schemaCode,
      parentSchema: schema,
      params: {},
      it
    };
  }
});

// node_modules/ajv/dist/compile/validate/defaults.js
var require_defaults = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.assignDefaults = void 0;
  var codegen_1 = require_codegen(), util_1 = require_util();
  function assignDefaults(it, ty) {
    let { properties, items } = it.schema;
    if (ty === "object" && properties)
      for (let key in properties)
        assignDefault(it, key, properties[key].default);
    else if (ty === "array" && Array.isArray(items))
      items.forEach((sch, i) => assignDefault(it, i, sch.default));
  }
  exports.assignDefaults = assignDefaults;
  function assignDefault(it, prop, defaultValue) {
    let { gen, compositeRule, data, opts } = it;
    if (defaultValue === void 0)
      return;
    let childData = codegen_1._`${data}${codegen_1.getProperty(prop)}`;
    if (compositeRule) {
      util_1.checkStrictMode(it, `default is ignored for: ${childData}`);
      return;
    }
    let condition = codegen_1._`${childData} === undefined`;
    if (opts.useDefaults === "empty")
      condition = codegen_1._`${condition} || ${childData} === null || ${childData} === ""`;
    gen.if(condition, codegen_1._`${childData} = ${codegen_1.stringify(defaultValue)}`);
  }
});

// node_modules/ajv/dist/vocabularies/code.js
var require_code2 = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.validateUnion = exports.validateArray = exports.usePattern = exports.callValidateCode = exports.schemaProperties = exports.allSchemaProperties = exports.noPropertyInData = exports.propertyInData = exports.isOwnProperty = exports.hasPropFunc = exports.reportMissingProp = exports.checkMissingProp = exports.checkReportMissingProp = void 0;
  var codegen_1 = require_codegen(), util_1 = require_util(), names_1 = require_names(), util_2 = require_util();
  function checkReportMissingProp(cxt, prop) {
    let { gen, data, it } = cxt;
    gen.if(noPropertyInData(gen, data, prop, it.opts.ownProperties), () => {
      cxt.setParams({ missingProperty: codegen_1._`${prop}` }, !0), cxt.error();
    });
  }
  exports.checkReportMissingProp = checkReportMissingProp;
  function checkMissingProp({ gen, data, it: { opts } }, properties, missing) {
    return codegen_1.or(...properties.map((prop) => codegen_1.and(noPropertyInData(gen, data, prop, opts.ownProperties), codegen_1._`${missing} = ${prop}`)));
  }
  exports.checkMissingProp = checkMissingProp;
  function reportMissingProp(cxt, missing) {
    cxt.setParams({ missingProperty: missing }, !0), cxt.error();
  }
  exports.reportMissingProp = reportMissingProp;
  function hasPropFunc(gen) {
    return gen.scopeValue("func", {
      ref: Object.prototype.hasOwnProperty,
      code: codegen_1._`Object.prototype.hasOwnProperty`
    });
  }
  exports.hasPropFunc = hasPropFunc;
  function isOwnProperty(gen, data, property) {
    return codegen_1._`${hasPropFunc(gen)}.call(${data}, ${property})`;
  }
  exports.isOwnProperty = isOwnProperty;
  function propertyInData(gen, data, property, ownProperties) {
    let cond = codegen_1._`${data}${codegen_1.getProperty(property)} !== undefined`;
    return ownProperties ? codegen_1._`${cond} && ${isOwnProperty(gen, data, property)}` : cond;
  }
  exports.propertyInData = propertyInData;
  function noPropertyInData(gen, data, property, ownProperties) {
    let cond = codegen_1._`${data}${codegen_1.getProperty(property)} === undefined`;
    return ownProperties ? codegen_1.or(cond, codegen_1.not(isOwnProperty(gen, data, property))) : cond;
  }
  exports.noPropertyInData = noPropertyInData;
  function allSchemaProperties(schemaMap) {
    return schemaMap ? Object.keys(schemaMap).filter((p) => p !== "__proto__") : [];
  }
  exports.allSchemaProperties = allSchemaProperties;
  function schemaProperties(it, schemaMap) {
    return allSchemaProperties(schemaMap).filter((p) => !util_1.alwaysValidSchema(it, schemaMap[p]));
  }
  exports.schemaProperties = schemaProperties;
  function callValidateCode({ schemaCode, data, it: { gen, topSchemaRef, schemaPath, errorPath }, it }, func, context, passSchema) {
    let dataAndSchema = passSchema ? codegen_1._`${schemaCode}, ${data}, ${topSchemaRef}${schemaPath}` : data, valCxt = [
      [names_1.default.instancePath, codegen_1.strConcat(names_1.default.instancePath, errorPath)],
      [names_1.default.parentData, it.parentData],
      [names_1.default.parentDataProperty, it.parentDataProperty],
      [names_1.default.rootData, names_1.default.rootData]
    ];
    if (it.opts.dynamicRef)
      valCxt.push([names_1.default.dynamicAnchors, names_1.default.dynamicAnchors]);
    let args = codegen_1._`${dataAndSchema}, ${gen.object(...valCxt)}`;
    return context !== codegen_1.nil ? codegen_1._`${func}.call(${context}, ${args})` : codegen_1._`${func}(${args})`;
  }
  exports.callValidateCode = callValidateCode;
  var newRegExp = codegen_1._`new RegExp`;
  function usePattern({ gen, it: { opts } }, pattern) {
    let u = opts.unicodeRegExp ? "u" : "", { regExp } = opts.code, rx = regExp(pattern, u);
    return gen.scopeValue("pattern", {
      key: rx.toString(),
      ref: rx,
      code: codegen_1._`${regExp.code === "new RegExp" ? newRegExp : util_2.useFunc(gen, regExp)}(${pattern}, ${u})`
    });
  }
  exports.usePattern = usePattern;
  function validateArray(cxt) {
    let { gen, data, keyword, it } = cxt, valid = gen.name("valid");
    if (it.allErrors) {
      let validArr = gen.let("valid", !0);
      return validateItems(() => gen.assign(validArr, !1)), validArr;
    }
    return gen.var(valid, !0), validateItems(() => gen.break()), valid;
    function validateItems(notValid) {
      let len = gen.const("len", codegen_1._`${data}.length`);
      gen.forRange("i", 0, len, (i) => {
        cxt.subschema({
          keyword,
          dataProp: i,
          dataPropType: util_1.Type.Num
        }, valid), gen.if(codegen_1.not(valid), notValid);
      });
    }
  }
  exports.validateArray = validateArray;
  function validateUnion(cxt) {
    let { gen, schema, keyword, it } = cxt;
    if (!Array.isArray(schema))
      throw Error("ajv implementation error");
    if (schema.some((sch) => util_1.alwaysValidSchema(it, sch)) && !it.opts.unevaluated)
      return;
    let valid = gen.let("valid", !1), schValid = gen.name("_valid");
    gen.block(() => schema.forEach((_sch, i) => {
      let schCxt = cxt.subschema({
        keyword,
        schemaProp: i,
        compositeRule: !0
      }, schValid);
      if (gen.assign(valid, codegen_1._`${valid} || ${schValid}`), !cxt.mergeValidEvaluated(schCxt, schValid))
        gen.if(codegen_1.not(valid));
    })), cxt.result(valid, () => cxt.reset(), () => cxt.error(!0));
  }
  exports.validateUnion = validateUnion;
});

// node_modules/ajv/dist/compile/validate/keyword.js
var require_keyword = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.validateKeywordUsage = exports.validSchemaType = exports.funcKeywordCode = exports.macroKeywordCode = void 0;
  var codegen_1 = require_codegen(), names_1 = require_names(), code_1 = require_code2(), errors_1 = require_errors();
  function macroKeywordCode(cxt, def) {
    let { gen, keyword, schema, parentSchema, it } = cxt, macroSchema = def.macro.call(it.self, schema, parentSchema, it), schemaRef = useKeyword(gen, keyword, macroSchema);
    if (it.opts.validateSchema !== !1)
      it.self.validateSchema(macroSchema, !0);
    let valid = gen.name("valid");
    cxt.subschema({
      schema: macroSchema,
      schemaPath: codegen_1.nil,
      errSchemaPath: `${it.errSchemaPath}/${keyword}`,
      topSchemaRef: schemaRef,
      compositeRule: !0
    }, valid), cxt.pass(valid, () => cxt.error(!0));
  }
  exports.macroKeywordCode = macroKeywordCode;
  function funcKeywordCode(cxt, def) {
    var _a;
    let { gen, keyword, schema, parentSchema, $data, it } = cxt;
    checkAsyncKeyword(it, def);
    let validate = !$data && def.compile ? def.compile.call(it.self, schema, parentSchema, it) : def.validate, validateRef = useKeyword(gen, keyword, validate), valid = gen.let("valid");
    cxt.block$data(valid, validateKeyword), cxt.ok((_a = def.valid) !== null && _a !== void 0 ? _a : valid);
    function validateKeyword() {
      if (def.errors === !1) {
        if (assignValid(), def.modifying)
          modifyData(cxt);
        reportErrs(() => cxt.error());
      } else {
        let ruleErrs = def.async ? validateAsync() : validateSync();
        if (def.modifying)
          modifyData(cxt);
        reportErrs(() => addErrs(cxt, ruleErrs));
      }
    }
    function validateAsync() {
      let ruleErrs = gen.let("ruleErrs", null);
      return gen.try(() => assignValid(codegen_1._`await `), (e) => gen.assign(valid, !1).if(codegen_1._`${e} instanceof ${it.ValidationError}`, () => gen.assign(ruleErrs, codegen_1._`${e}.errors`), () => gen.throw(e))), ruleErrs;
    }
    function validateSync() {
      let validateErrs = codegen_1._`${validateRef}.errors`;
      return gen.assign(validateErrs, null), assignValid(codegen_1.nil), validateErrs;
    }
    function assignValid(_await = def.async ? codegen_1._`await ` : codegen_1.nil) {
      let passCxt = it.opts.passContext ? names_1.default.this : names_1.default.self, passSchema = !(("compile" in def) && !$data || def.schema === !1);
      gen.assign(valid, codegen_1._`${_await}${code_1.callValidateCode(cxt, validateRef, passCxt, passSchema)}`, def.modifying);
    }
    function reportErrs(errors) {
      var _a;
      gen.if(codegen_1.not((_a = def.valid) !== null && _a !== void 0 ? _a : valid), errors);
    }
  }
  exports.funcKeywordCode = funcKeywordCode;
  function modifyData(cxt) {
    let { gen, data, it } = cxt;
    gen.if(it.parentData, () => gen.assign(data, codegen_1._`${it.parentData}[${it.parentDataProperty}]`));
  }
  function addErrs(cxt, errs) {
    let { gen } = cxt;
    gen.if(codegen_1._`Array.isArray(${errs})`, () => {
      gen.assign(names_1.default.vErrors, codegen_1._`${names_1.default.vErrors} === null ? ${errs} : ${names_1.default.vErrors}.concat(${errs})`).assign(names_1.default.errors, codegen_1._`${names_1.default.vErrors}.length`), errors_1.extendErrors(cxt);
    }, () => cxt.error());
  }
  function checkAsyncKeyword({ schemaEnv }, def) {
    if (def.async && !schemaEnv.$async)
      throw Error("async keyword in sync schema");
  }
  function useKeyword(gen, keyword, result) {
    if (result === void 0)
      throw Error(`keyword "${keyword}" failed to compile`);
    return gen.scopeValue("keyword", typeof result == "function" ? { ref: result } : { ref: result, code: codegen_1.stringify(result) });
  }
  function validSchemaType(schema, schemaType, allowUndefined = !1) {
    return !schemaType.length || schemaType.some((st) => st === "array" ? Array.isArray(schema) : st === "object" ? schema && typeof schema == "object" && !Array.isArray(schema) : typeof schema == st || allowUndefined && typeof schema > "u");
  }
  exports.validSchemaType = validSchemaType;
  function validateKeywordUsage({ schema, opts, self, errSchemaPath }, def, keyword) {
    if (Array.isArray(def.keyword) ? !def.keyword.includes(keyword) : def.keyword !== keyword)
      throw Error("ajv implementation error");
    let deps = def.dependencies;
    if (deps === null || deps === void 0 ? void 0 : deps.some((kwd) => !Object.prototype.hasOwnProperty.call(schema, kwd)))
      throw Error(`parent schema must have dependencies of ${keyword}: ${deps.join(",")}`);
    if (def.validateSchema) {
      if (!def.validateSchema(schema[keyword])) {
        let msg = `keyword "${keyword}" value is invalid at path "${errSchemaPath}": ` + self.errorsText(def.validateSchema.errors);
        if (opts.validateSchema === "log")
          self.logger.error(msg);
        else
          throw Error(msg);
      }
    }
  }
  exports.validateKeywordUsage = validateKeywordUsage;
});

// node_modules/ajv/dist/compile/validate/subschema.js
var require_subschema = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.extendSubschemaMode = exports.extendSubschemaData = exports.getSubschema = void 0;
  var codegen_1 = require_codegen(), util_1 = require_util();
  function getSubschema(it, { keyword, schemaProp, schema, schemaPath, errSchemaPath, topSchemaRef }) {
    if (keyword !== void 0 && schema !== void 0)
      throw Error('both "keyword" and "schema" passed, only one allowed');
    if (keyword !== void 0) {
      let sch = it.schema[keyword];
      return schemaProp === void 0 ? {
        schema: sch,
        schemaPath: codegen_1._`${it.schemaPath}${codegen_1.getProperty(keyword)}`,
        errSchemaPath: `${it.errSchemaPath}/${keyword}`
      } : {
        schema: sch[schemaProp],
        schemaPath: codegen_1._`${it.schemaPath}${codegen_1.getProperty(keyword)}${codegen_1.getProperty(schemaProp)}`,
        errSchemaPath: `${it.errSchemaPath}/${keyword}/${util_1.escapeFragment(schemaProp)}`
      };
    }
    if (schema !== void 0) {
      if (schemaPath === void 0 || errSchemaPath === void 0 || topSchemaRef === void 0)
        throw Error('"schemaPath", "errSchemaPath" and "topSchemaRef" are required with "schema"');
      return {
        schema,
        schemaPath,
        topSchemaRef,
        errSchemaPath
      };
    }
    throw Error('either "keyword" or "schema" must be passed');
  }
  exports.getSubschema = getSubschema;
  function extendSubschemaData(subschema, it, { dataProp, dataPropType: dpType, data, dataTypes, propertyName }) {
    if (data !== void 0 && dataProp !== void 0)
      throw Error('both "data" and "dataProp" passed, only one allowed');
    let { gen } = it;
    if (dataProp !== void 0) {
      let { errorPath, dataPathArr, opts } = it, nextData = gen.let("data", codegen_1._`${it.data}${codegen_1.getProperty(dataProp)}`, !0);
      dataContextProps(nextData), subschema.errorPath = codegen_1.str`${errorPath}${util_1.getErrorPath(dataProp, dpType, opts.jsPropertySyntax)}`, subschema.parentDataProperty = codegen_1._`${dataProp}`, subschema.dataPathArr = [...dataPathArr, subschema.parentDataProperty];
    }
    if (data !== void 0) {
      let nextData = data instanceof codegen_1.Name ? data : gen.let("data", data, !0);
      if (dataContextProps(nextData), propertyName !== void 0)
        subschema.propertyName = propertyName;
    }
    if (dataTypes)
      subschema.dataTypes = dataTypes;
    function dataContextProps(_nextData) {
      subschema.data = _nextData, subschema.dataLevel = it.dataLevel + 1, subschema.dataTypes = [], it.definedProperties = /* @__PURE__ */ new Set, subschema.parentData = it.data, subschema.dataNames = [...it.dataNames, _nextData];
    }
  }
  exports.extendSubschemaData = extendSubschemaData;
  function extendSubschemaMode(subschema, { jtdDiscriminator, jtdMetadata, compositeRule, createErrors, allErrors }) {
    if (compositeRule !== void 0)
      subschema.compositeRule = compositeRule;
    if (createErrors !== void 0)
      subschema.createErrors = createErrors;
    if (allErrors !== void 0)
      subschema.allErrors = allErrors;
    subschema.jtdDiscriminator = jtdDiscriminator, subschema.jtdMetadata = jtdMetadata;
  }
  exports.extendSubschemaMode = extendSubschemaMode;
});

// node_modules/fast-deep-equal/index.js
var require_fast_deep_equal = __commonJS(function(exports, module) {
  module.exports = function equal(a, b) {
    if (a === b)
      return !0;
    if (a && b && typeof a == "object" && typeof b == "object") {
      if (a.constructor !== b.constructor)
        return !1;
      var length, i, keys;
      if (Array.isArray(a)) {
        if (length = a.length, length != b.length)
          return !1;
        for (i = length;i-- !== 0; )
          if (!equal(a[i], b[i]))
            return !1;
        return !0;
      }
      if (a.constructor === RegExp)
        return a.source === b.source && a.flags === b.flags;
      if (a.valueOf !== Object.prototype.valueOf)
        return a.valueOf() === b.valueOf();
      if (a.toString !== Object.prototype.toString)
        return a.toString() === b.toString();
      if (keys = Object.keys(a), length = keys.length, length !== Object.keys(b).length)
        return !1;
      for (i = length;i-- !== 0; )
        if (!Object.prototype.hasOwnProperty.call(b, keys[i]))
          return !1;
      for (i = length;i-- !== 0; ) {
        var key = keys[i];
        if (!equal(a[key], b[key]))
          return !1;
      }
      return !0;
    }
    return a !== a && b !== b;
  };
});

// node_modules/json-schema-traverse/index.js
var require_json_schema_traverse = __commonJS(function(exports, module) {
  var traverse = module.exports = function(schema, opts, cb) {
    if (typeof opts == "function")
      cb = opts, opts = {};
    cb = opts.cb || cb;
    var pre = typeof cb == "function" ? cb : cb.pre || function() {}, post = cb.post || function() {};
    _traverse(opts, pre, post, schema, "", schema);
  };
  traverse.keywords = {
    additionalItems: !0,
    items: !0,
    contains: !0,
    additionalProperties: !0,
    propertyNames: !0,
    not: !0,
    if: !0,
    then: !0,
    else: !0
  };
  traverse.arrayKeywords = {
    items: !0,
    allOf: !0,
    anyOf: !0,
    oneOf: !0
  };
  traverse.propsKeywords = {
    $defs: !0,
    definitions: !0,
    properties: !0,
    patternProperties: !0,
    dependencies: !0
  };
  traverse.skipKeywords = {
    default: !0,
    enum: !0,
    const: !0,
    required: !0,
    maximum: !0,
    minimum: !0,
    exclusiveMaximum: !0,
    exclusiveMinimum: !0,
    multipleOf: !0,
    maxLength: !0,
    minLength: !0,
    pattern: !0,
    format: !0,
    maxItems: !0,
    minItems: !0,
    uniqueItems: !0,
    maxProperties: !0,
    minProperties: !0
  };
  function _traverse(opts, pre, post, schema, jsonPtr, rootSchema, parentJsonPtr, parentKeyword, parentSchema, keyIndex) {
    if (schema && typeof schema == "object" && !Array.isArray(schema)) {
      pre(schema, jsonPtr, rootSchema, parentJsonPtr, parentKeyword, parentSchema, keyIndex);
      for (var key in schema) {
        var sch = schema[key];
        if (Array.isArray(sch)) {
          if (key in traverse.arrayKeywords)
            for (var i = 0;i < sch.length; i++)
              _traverse(opts, pre, post, sch[i], jsonPtr + "/" + key + "/" + i, rootSchema, jsonPtr, key, schema, i);
        } else if (key in traverse.propsKeywords) {
          if (sch && typeof sch == "object")
            for (var prop in sch)
              _traverse(opts, pre, post, sch[prop], jsonPtr + "/" + key + "/" + escapeJsonPtr(prop), rootSchema, jsonPtr, key, schema, prop);
        } else if (key in traverse.keywords || opts.allKeys && !(key in traverse.skipKeywords))
          _traverse(opts, pre, post, sch, jsonPtr + "/" + key, rootSchema, jsonPtr, key, schema);
      }
      post(schema, jsonPtr, rootSchema, parentJsonPtr, parentKeyword, parentSchema, keyIndex);
    }
  }
  function escapeJsonPtr(str) {
    return str.replace(/~/g, "~0").replace(/\//g, "~1");
  }
});

// node_modules/ajv/dist/compile/resolve.js
var require_resolve = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.getSchemaRefs = exports.resolveUrl = exports.normalizeId = exports._getFullPath = exports.getFullPath = exports.inlineRef = void 0;
  var util_1 = require_util(), equal = require_fast_deep_equal(), traverse = require_json_schema_traverse(), SIMPLE_INLINED = /* @__PURE__ */ new Set([
    "type",
    "format",
    "pattern",
    "maxLength",
    "minLength",
    "maxProperties",
    "minProperties",
    "maxItems",
    "minItems",
    "maximum",
    "minimum",
    "uniqueItems",
    "multipleOf",
    "required",
    "enum",
    "const"
  ]);
  function inlineRef(schema, limit = !0) {
    if (typeof schema == "boolean")
      return !0;
    if (limit === !0)
      return !hasRef(schema);
    if (!limit)
      return !1;
    return countKeys(schema) <= limit;
  }
  exports.inlineRef = inlineRef;
  var REF_KEYWORDS = /* @__PURE__ */ new Set([
    "$ref",
    "$recursiveRef",
    "$recursiveAnchor",
    "$dynamicRef",
    "$dynamicAnchor"
  ]);
  function hasRef(schema) {
    for (let key in schema) {
      if (REF_KEYWORDS.has(key))
        return !0;
      let sch = schema[key];
      if (Array.isArray(sch) && sch.some(hasRef))
        return !0;
      if (typeof sch == "object" && hasRef(sch))
        return !0;
    }
    return !1;
  }
  function countKeys(schema) {
    let count = 0;
    for (let key in schema) {
      if (key === "$ref")
        return 1 / 0;
      if (count++, SIMPLE_INLINED.has(key))
        continue;
      if (typeof schema[key] == "object")
        util_1.eachItem(schema[key], (sch) => count += countKeys(sch));
      if (count === 1 / 0)
        return 1 / 0;
    }
    return count;
  }
  function getFullPath(resolver, id = "", normalize) {
    if (normalize !== !1)
      id = normalizeId(id);
    let p = resolver.parse(id);
    return _getFullPath(resolver, p);
  }
  exports.getFullPath = getFullPath;
  function _getFullPath(resolver, p) {
    return resolver.serialize(p).split("#")[0] + "#";
  }
  exports._getFullPath = _getFullPath;
  var TRAILING_SLASH_HASH = /#\/?$/;
  function normalizeId(id) {
    return id ? id.replace(TRAILING_SLASH_HASH, "") : "";
  }
  exports.normalizeId = normalizeId;
  function resolveUrl(resolver, baseId, id) {
    return id = normalizeId(id), resolver.resolve(baseId, id);
  }
  exports.resolveUrl = resolveUrl;
  var ANCHOR = /^[a-z_][-a-z0-9._]*$/i;
  function getSchemaRefs(schema, baseId) {
    if (typeof schema == "boolean")
      return {};
    let { schemaId, uriResolver } = this.opts, schId = normalizeId(schema[schemaId] || baseId), baseIds = { "": schId }, pathPrefix = getFullPath(uriResolver, schId, !1), localRefs = {}, schemaRefs = /* @__PURE__ */ new Set;
    return traverse(schema, { allKeys: !0 }, (sch, jsonPtr, _, parentJsonPtr) => {
      if (parentJsonPtr === void 0)
        return;
      let fullPath = pathPrefix + jsonPtr, innerBaseId = baseIds[parentJsonPtr];
      if (typeof sch[schemaId] == "string")
        innerBaseId = addRef.call(this, sch[schemaId]);
      addAnchor.call(this, sch.$anchor), addAnchor.call(this, sch.$dynamicAnchor), baseIds[jsonPtr] = innerBaseId;
      function addRef(ref) {
        let _resolve = this.opts.uriResolver.resolve;
        if (ref = normalizeId(innerBaseId ? _resolve(innerBaseId, ref) : ref), schemaRefs.has(ref))
          throw ambiguos(ref);
        schemaRefs.add(ref);
        let schOrRef = this.refs[ref];
        if (typeof schOrRef == "string")
          schOrRef = this.refs[schOrRef];
        if (typeof schOrRef == "object")
          checkAmbiguosRef(sch, schOrRef.schema, ref);
        else if (ref !== normalizeId(fullPath))
          if (ref[0] === "#")
            checkAmbiguosRef(sch, localRefs[ref], ref), localRefs[ref] = sch;
          else
            this.refs[ref] = fullPath;
        return ref;
      }
      function addAnchor(anchor) {
        if (typeof anchor == "string") {
          if (!ANCHOR.test(anchor))
            throw Error(`invalid anchor "${anchor}"`);
          addRef.call(this, `#${anchor}`);
        }
      }
    }), localRefs;
    function checkAmbiguosRef(sch1, sch2, ref) {
      if (sch2 !== void 0 && !equal(sch1, sch2))
        throw ambiguos(ref);
    }
    function ambiguos(ref) {
      return Error(`reference "${ref}" resolves to more than one schema`);
    }
  }
  exports.getSchemaRefs = getSchemaRefs;
});

// node_modules/ajv/dist/compile/validate/index.js
var require_validate = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.getData = exports.KeywordCxt = exports.validateFunctionCode = void 0;
  var boolSchema_1 = require_boolSchema(), dataType_1 = require_dataType(), applicability_1 = require_applicability(), dataType_2 = require_dataType(), defaults_1 = require_defaults(), keyword_1 = require_keyword(), subschema_1 = require_subschema(), codegen_1 = require_codegen(), names_1 = require_names(), resolve_1 = require_resolve(), util_1 = require_util(), errors_1 = require_errors();
  function validateFunctionCode(it) {
    if (isSchemaObj(it)) {
      if (checkKeywords(it), schemaCxtHasRules(it)) {
        topSchemaObjCode(it);
        return;
      }
    }
    validateFunction(it, () => boolSchema_1.topBoolOrEmptySchema(it));
  }
  exports.validateFunctionCode = validateFunctionCode;
  function validateFunction({ gen, validateName, schema, schemaEnv, opts }, body) {
    if (opts.code.es5)
      gen.func(validateName, codegen_1._`${names_1.default.data}, ${names_1.default.valCxt}`, schemaEnv.$async, () => {
        gen.code(codegen_1._`"use strict"; ${funcSourceUrl(schema, opts)}`), destructureValCxtES5(gen, opts), gen.code(body);
      });
    else
      gen.func(validateName, codegen_1._`${names_1.default.data}, ${destructureValCxt(opts)}`, schemaEnv.$async, () => gen.code(funcSourceUrl(schema, opts)).code(body));
  }
  function destructureValCxt(opts) {
    return codegen_1._`{${names_1.default.instancePath}="", ${names_1.default.parentData}, ${names_1.default.parentDataProperty}, ${names_1.default.rootData}=${names_1.default.data}${opts.dynamicRef ? codegen_1._`, ${names_1.default.dynamicAnchors}={}` : codegen_1.nil}}={}`;
  }
  function destructureValCxtES5(gen, opts) {
    gen.if(names_1.default.valCxt, () => {
      if (gen.var(names_1.default.instancePath, codegen_1._`${names_1.default.valCxt}.${names_1.default.instancePath}`), gen.var(names_1.default.parentData, codegen_1._`${names_1.default.valCxt}.${names_1.default.parentData}`), gen.var(names_1.default.parentDataProperty, codegen_1._`${names_1.default.valCxt}.${names_1.default.parentDataProperty}`), gen.var(names_1.default.rootData, codegen_1._`${names_1.default.valCxt}.${names_1.default.rootData}`), opts.dynamicRef)
        gen.var(names_1.default.dynamicAnchors, codegen_1._`${names_1.default.valCxt}.${names_1.default.dynamicAnchors}`);
    }, () => {
      if (gen.var(names_1.default.instancePath, codegen_1._`""`), gen.var(names_1.default.parentData, codegen_1._`undefined`), gen.var(names_1.default.parentDataProperty, codegen_1._`undefined`), gen.var(names_1.default.rootData, names_1.default.data), opts.dynamicRef)
        gen.var(names_1.default.dynamicAnchors, codegen_1._`{}`);
    });
  }
  function topSchemaObjCode(it) {
    let { schema, opts, gen } = it;
    validateFunction(it, () => {
      if (opts.$comment && schema.$comment)
        commentKeyword(it);
      if (checkNoDefault(it), gen.let(names_1.default.vErrors, null), gen.let(names_1.default.errors, 0), opts.unevaluated)
        resetEvaluated(it);
      typeAndKeywords(it), returnResults(it);
    });
    return;
  }
  function resetEvaluated(it) {
    let { gen, validateName } = it;
    it.evaluated = gen.const("evaluated", codegen_1._`${validateName}.evaluated`), gen.if(codegen_1._`${it.evaluated}.dynamicProps`, () => gen.assign(codegen_1._`${it.evaluated}.props`, codegen_1._`undefined`)), gen.if(codegen_1._`${it.evaluated}.dynamicItems`, () => gen.assign(codegen_1._`${it.evaluated}.items`, codegen_1._`undefined`));
  }
  function funcSourceUrl(schema, opts) {
    let schId = typeof schema == "object" && schema[opts.schemaId];
    return schId && (opts.code.source || opts.code.process) ? codegen_1._`/*# sourceURL=${schId} */` : codegen_1.nil;
  }
  function subschemaCode(it, valid) {
    if (isSchemaObj(it)) {
      if (checkKeywords(it), schemaCxtHasRules(it)) {
        subSchemaObjCode(it, valid);
        return;
      }
    }
    boolSchema_1.boolOrEmptySchema(it, valid);
  }
  function schemaCxtHasRules({ schema, self }) {
    if (typeof schema == "boolean")
      return !schema;
    for (let key in schema)
      if (self.RULES.all[key])
        return !0;
    return !1;
  }
  function isSchemaObj(it) {
    return typeof it.schema != "boolean";
  }
  function subSchemaObjCode(it, valid) {
    let { schema, gen, opts } = it;
    if (opts.$comment && schema.$comment)
      commentKeyword(it);
    updateContext(it), checkAsyncSchema(it);
    let errsCount = gen.const("_errs", names_1.default.errors);
    typeAndKeywords(it, errsCount), gen.var(valid, codegen_1._`${errsCount} === ${names_1.default.errors}`);
  }
  function checkKeywords(it) {
    util_1.checkUnknownRules(it), checkRefsAndKeywords(it);
  }
  function typeAndKeywords(it, errsCount) {
    if (it.opts.jtd)
      return schemaKeywords(it, [], !1, errsCount);
    let types = dataType_1.getSchemaTypes(it.schema), checkedTypes = dataType_1.coerceAndCheckDataType(it, types);
    schemaKeywords(it, types, !checkedTypes, errsCount);
  }
  function checkRefsAndKeywords(it) {
    let { schema, errSchemaPath, opts, self } = it;
    if (schema.$ref && opts.ignoreKeywordsWithRef && util_1.schemaHasRulesButRef(schema, self.RULES))
      self.logger.warn(`$ref: keywords ignored in schema at path "${errSchemaPath}"`);
  }
  function checkNoDefault(it) {
    let { schema, opts } = it;
    if (schema.default !== void 0 && opts.useDefaults && opts.strictSchema)
      util_1.checkStrictMode(it, "default is ignored in the schema root");
  }
  function updateContext(it) {
    let schId = it.schema[it.opts.schemaId];
    if (schId)
      it.baseId = resolve_1.resolveUrl(it.opts.uriResolver, it.baseId, schId);
  }
  function checkAsyncSchema(it) {
    if (it.schema.$async && !it.schemaEnv.$async)
      throw Error("async schema in sync schema");
  }
  function commentKeyword({ gen, schemaEnv, schema, errSchemaPath, opts }) {
    let msg = schema.$comment;
    if (opts.$comment === !0)
      gen.code(codegen_1._`${names_1.default.self}.logger.log(${msg})`);
    else if (typeof opts.$comment == "function") {
      let schemaPath = codegen_1.str`${errSchemaPath}/$comment`, rootName = gen.scopeValue("root", { ref: schemaEnv.root });
      gen.code(codegen_1._`${names_1.default.self}.opts.$comment(${msg}, ${schemaPath}, ${rootName}.schema)`);
    }
  }
  function returnResults(it) {
    let { gen, schemaEnv, validateName, ValidationError, opts } = it;
    if (schemaEnv.$async)
      gen.if(codegen_1._`${names_1.default.errors} === 0`, () => gen.return(names_1.default.data), () => gen.throw(codegen_1._`new ${ValidationError}(${names_1.default.vErrors})`));
    else {
      if (gen.assign(codegen_1._`${validateName}.errors`, names_1.default.vErrors), opts.unevaluated)
        assignEvaluated(it);
      gen.return(codegen_1._`${names_1.default.errors} === 0`);
    }
  }
  function assignEvaluated({ gen, evaluated, props, items }) {
    if (props instanceof codegen_1.Name)
      gen.assign(codegen_1._`${evaluated}.props`, props);
    if (items instanceof codegen_1.Name)
      gen.assign(codegen_1._`${evaluated}.items`, items);
  }
  function schemaKeywords(it, types, typeErrors, errsCount) {
    let { gen, schema, data, allErrors, opts, self } = it, { RULES } = self;
    if (schema.$ref && (opts.ignoreKeywordsWithRef || !util_1.schemaHasRulesButRef(schema, RULES))) {
      gen.block(() => keywordCode(it, "$ref", RULES.all.$ref.definition));
      return;
    }
    if (!opts.jtd)
      checkStrictTypes(it, types);
    gen.block(() => {
      for (let group of RULES.rules)
        groupKeywords(group);
      groupKeywords(RULES.post);
    });
    function groupKeywords(group) {
      if (!applicability_1.shouldUseGroup(schema, group))
        return;
      if (group.type) {
        if (gen.if(dataType_2.checkDataType(group.type, data, opts.strictNumbers)), iterateKeywords(it, group), types.length === 1 && types[0] === group.type && typeErrors)
          gen.else(), dataType_2.reportTypeError(it);
        gen.endIf();
      } else
        iterateKeywords(it, group);
      if (!allErrors)
        gen.if(codegen_1._`${names_1.default.errors} === ${errsCount || 0}`);
    }
  }
  function iterateKeywords(it, group) {
    let { gen, schema, opts: { useDefaults } } = it;
    if (useDefaults)
      defaults_1.assignDefaults(it, group.type);
    gen.block(() => {
      for (let rule of group.rules)
        if (applicability_1.shouldUseRule(schema, rule))
          keywordCode(it, rule.keyword, rule.definition, group.type);
    });
  }
  function checkStrictTypes(it, types) {
    if (it.schemaEnv.meta || !it.opts.strictTypes)
      return;
    if (checkContextTypes(it, types), !it.opts.allowUnionTypes)
      checkMultipleTypes(it, types);
    checkKeywordTypes(it, it.dataTypes);
  }
  function checkContextTypes(it, types) {
    if (!types.length)
      return;
    if (!it.dataTypes.length) {
      it.dataTypes = types;
      return;
    }
    types.forEach((t) => {
      if (!includesType(it.dataTypes, t))
        strictTypesError(it, `type "${t}" not allowed by context "${it.dataTypes.join(",")}"`);
    }), narrowSchemaTypes(it, types);
  }
  function checkMultipleTypes(it, ts) {
    if (ts.length > 1 && !(ts.length === 2 && ts.includes("null")))
      strictTypesError(it, "use allowUnionTypes to allow union type keyword");
  }
  function checkKeywordTypes(it, ts) {
    let rules = it.self.RULES.all;
    for (let keyword in rules) {
      let rule = rules[keyword];
      if (typeof rule == "object" && applicability_1.shouldUseRule(it.schema, rule)) {
        let { type } = rule.definition;
        if (type.length && !type.some((t) => hasApplicableType(ts, t)))
          strictTypesError(it, `missing type "${type.join(",")}" for keyword "${keyword}"`);
      }
    }
  }
  function hasApplicableType(schTs, kwdT) {
    return schTs.includes(kwdT) || kwdT === "number" && schTs.includes("integer");
  }
  function includesType(ts, t) {
    return ts.includes(t) || t === "integer" && ts.includes("number");
  }
  function narrowSchemaTypes(it, withTypes) {
    let ts = [];
    for (let t of it.dataTypes)
      if (includesType(withTypes, t))
        ts.push(t);
      else if (withTypes.includes("integer") && t === "number")
        ts.push("integer");
    it.dataTypes = ts;
  }
  function strictTypesError(it, msg) {
    let schemaPath = it.schemaEnv.baseId + it.errSchemaPath;
    msg += ` at "${schemaPath}" (strictTypes)`, util_1.checkStrictMode(it, msg, it.opts.strictTypes);
  }

  class KeywordCxt {
    constructor(it, def, keyword) {
      if (keyword_1.validateKeywordUsage(it, def, keyword), this.gen = it.gen, this.allErrors = it.allErrors, this.keyword = keyword, this.data = it.data, this.schema = it.schema[keyword], this.$data = def.$data && it.opts.$data && this.schema && this.schema.$data, this.schemaValue = util_1.schemaRefOrVal(it, this.schema, keyword, this.$data), this.schemaType = def.schemaType, this.parentSchema = it.schema, this.params = {}, this.it = it, this.def = def, this.$data)
        this.schemaCode = it.gen.const("vSchema", getData(this.$data, it));
      else if (this.schemaCode = this.schemaValue, !keyword_1.validSchemaType(this.schema, def.schemaType, def.allowUndefined))
        throw Error(`${keyword} value must be ${JSON.stringify(def.schemaType)}`);
      if ("code" in def ? def.trackErrors : def.errors !== !1)
        this.errsCount = it.gen.const("_errs", names_1.default.errors);
    }
    result(condition, successAction, failAction) {
      this.failResult(codegen_1.not(condition), successAction, failAction);
    }
    failResult(condition, successAction, failAction) {
      if (this.gen.if(condition), failAction)
        failAction();
      else
        this.error();
      if (successAction) {
        if (this.gen.else(), successAction(), this.allErrors)
          this.gen.endIf();
      } else if (this.allErrors)
        this.gen.endIf();
      else
        this.gen.else();
    }
    pass(condition, failAction) {
      this.failResult(codegen_1.not(condition), void 0, failAction);
    }
    fail(condition) {
      if (condition === void 0) {
        if (this.error(), !this.allErrors)
          this.gen.if(!1);
        return;
      }
      if (this.gen.if(condition), this.error(), this.allErrors)
        this.gen.endIf();
      else
        this.gen.else();
    }
    fail$data(condition) {
      if (!this.$data)
        return this.fail(condition);
      let { schemaCode } = this;
      this.fail(codegen_1._`${schemaCode} !== undefined && (${codegen_1.or(this.invalid$data(), condition)})`);
    }
    error(append, errorParams, errorPaths) {
      if (errorParams) {
        this.setParams(errorParams), this._error(append, errorPaths), this.setParams({});
        return;
      }
      this._error(append, errorPaths);
    }
    _error(append, errorPaths) {
      (append ? errors_1.reportExtraError : errors_1.reportError)(this, this.def.error, errorPaths);
    }
    $dataError() {
      errors_1.reportError(this, this.def.$dataError || errors_1.keyword$DataError);
    }
    reset() {
      if (this.errsCount === void 0)
        throw Error('add "trackErrors" to keyword definition');
      errors_1.resetErrorsCount(this.gen, this.errsCount);
    }
    ok(cond) {
      if (!this.allErrors)
        this.gen.if(cond);
    }
    setParams(obj, assign) {
      if (assign)
        Object.assign(this.params, obj);
      else
        this.params = obj;
    }
    block$data(valid, codeBlock, $dataValid = codegen_1.nil) {
      this.gen.block(() => {
        this.check$data(valid, $dataValid), codeBlock();
      });
    }
    check$data(valid = codegen_1.nil, $dataValid = codegen_1.nil) {
      if (!this.$data)
        return;
      let { gen, schemaCode, schemaType, def } = this;
      if (gen.if(codegen_1.or(codegen_1._`${schemaCode} === undefined`, $dataValid)), valid !== codegen_1.nil)
        gen.assign(valid, !0);
      if (schemaType.length || def.validateSchema) {
        if (gen.elseIf(this.invalid$data()), this.$dataError(), valid !== codegen_1.nil)
          gen.assign(valid, !1);
      }
      gen.else();
    }
    invalid$data() {
      let { gen, schemaCode, schemaType, def, it } = this;
      return codegen_1.or(wrong$DataType(), invalid$DataSchema());
      function wrong$DataType() {
        if (schemaType.length) {
          if (!(schemaCode instanceof codegen_1.Name))
            throw Error("ajv implementation error");
          let st = Array.isArray(schemaType) ? schemaType : [schemaType];
          return codegen_1._`${dataType_2.checkDataTypes(st, schemaCode, it.opts.strictNumbers, dataType_2.DataType.Wrong)}`;
        }
        return codegen_1.nil;
      }
      function invalid$DataSchema() {
        if (def.validateSchema) {
          let validateSchemaRef = gen.scopeValue("validate$data", { ref: def.validateSchema });
          return codegen_1._`!${validateSchemaRef}(${schemaCode})`;
        }
        return codegen_1.nil;
      }
    }
    subschema(appl, valid) {
      let subschema = subschema_1.getSubschema(this.it, appl);
      subschema_1.extendSubschemaData(subschema, this.it, appl), subschema_1.extendSubschemaMode(subschema, appl);
      let nextContext = { ...this.it, ...subschema, items: void 0, props: void 0 };
      return subschemaCode(nextContext, valid), nextContext;
    }
    mergeEvaluated(schemaCxt, toName) {
      let { it, gen } = this;
      if (!it.opts.unevaluated)
        return;
      if (it.props !== !0 && schemaCxt.props !== void 0)
        it.props = util_1.mergeEvaluated.props(gen, schemaCxt.props, it.props, toName);
      if (it.items !== !0 && schemaCxt.items !== void 0)
        it.items = util_1.mergeEvaluated.items(gen, schemaCxt.items, it.items, toName);
    }
    mergeValidEvaluated(schemaCxt, valid) {
      let { it, gen } = this;
      if (it.opts.unevaluated && (it.props !== !0 || it.items !== !0))
        return gen.if(valid, () => this.mergeEvaluated(schemaCxt, codegen_1.Name)), !0;
    }
  }
  exports.KeywordCxt = KeywordCxt;
  function keywordCode(it, keyword, def, ruleType) {
    let cxt = new KeywordCxt(it, def, keyword);
    if ("code" in def)
      def.code(cxt, ruleType);
    else if (cxt.$data && def.validate)
      keyword_1.funcKeywordCode(cxt, def);
    else if ("macro" in def)
      keyword_1.macroKeywordCode(cxt, def);
    else if (def.compile || def.validate)
      keyword_1.funcKeywordCode(cxt, def);
  }
  var JSON_POINTER = /^\/(?:[^~]|~0|~1)*$/, RELATIVE_JSON_POINTER = /^([0-9]+)(#|\/(?:[^~]|~0|~1)*)?$/;
  function getData($data, { dataLevel, dataNames, dataPathArr }) {
    let jsonPointer, data;
    if ($data === "")
      return names_1.default.rootData;
    if ($data[0] === "/") {
      if (!JSON_POINTER.test($data))
        throw Error(`Invalid JSON-pointer: ${$data}`);
      jsonPointer = $data, data = names_1.default.rootData;
    } else {
      let matches = RELATIVE_JSON_POINTER.exec($data);
      if (!matches)
        throw Error(`Invalid JSON-pointer: ${$data}`);
      let up = +matches[1];
      if (jsonPointer = matches[2], jsonPointer === "#") {
        if (up >= dataLevel)
          throw Error(errorMsg("property/index", up));
        return dataPathArr[dataLevel - up];
      }
      if (up > dataLevel)
        throw Error(errorMsg("data", up));
      if (data = dataNames[dataLevel - up], !jsonPointer)
        return data;
    }
    let expr = data, segments = jsonPointer.split("/");
    for (let segment of segments)
      if (segment)
        data = codegen_1._`${data}${codegen_1.getProperty(util_1.unescapeJsonPointer(segment))}`, expr = codegen_1._`${expr} && ${data}`;
    return expr;
    function errorMsg(pointerType, up) {
      return `Cannot access ${pointerType} ${up} levels up, current level is ${dataLevel}`;
    }
  }
  exports.getData = getData;
});

// node_modules/ajv/dist/runtime/validation_error.js
var require_validation_error = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });

  class ValidationError extends Error {
    constructor(errors) {
      super("validation failed");
      this.errors = errors, this.ajv = this.validation = !0;
    }
  }
  exports.default = ValidationError;
});

// node_modules/ajv/dist/compile/ref_error.js
var require_ref_error = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var resolve_1 = require_resolve();

  class MissingRefError extends Error {
    constructor(resolver, baseId, ref, msg) {
      super(msg || `can't resolve reference ${ref} from id ${baseId}`);
      this.missingRef = resolve_1.resolveUrl(resolver, baseId, ref), this.missingSchema = resolve_1.normalizeId(resolve_1.getFullPath(resolver, this.missingRef));
    }
  }
  exports.default = MissingRefError;
});

// node_modules/ajv/dist/compile/index.js
var require_compile = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.resolveSchema = exports.getCompilingSchema = exports.resolveRef = exports.compileSchema = exports.SchemaEnv = void 0;
  var codegen_1 = require_codegen(), validation_error_1 = require_validation_error(), names_1 = require_names(), resolve_1 = require_resolve(), util_1 = require_util(), validate_1 = require_validate();

  class SchemaEnv {
    constructor(env) {
      var _a;
      this.refs = {}, this.dynamicAnchors = {};
      let schema;
      if (typeof env.schema == "object")
        schema = env.schema;
      this.schema = env.schema, this.schemaId = env.schemaId, this.root = env.root || this, this.baseId = (_a = env.baseId) !== null && _a !== void 0 ? _a : resolve_1.normalizeId(schema === null || schema === void 0 ? void 0 : schema[env.schemaId || "$id"]), this.schemaPath = env.schemaPath, this.localRefs = env.localRefs, this.meta = env.meta, this.$async = schema === null || schema === void 0 ? void 0 : schema.$async, this.refs = {};
    }
  }
  exports.SchemaEnv = SchemaEnv;
  function compileSchema(sch) {
    let _sch = getCompilingSchema.call(this, sch);
    if (_sch)
      return _sch;
    let rootId = resolve_1.getFullPath(this.opts.uriResolver, sch.root.baseId), { es5, lines } = this.opts.code, { ownProperties } = this.opts, gen = new codegen_1.CodeGen(this.scope, { es5, lines, ownProperties }), _ValidationError;
    if (sch.$async)
      _ValidationError = gen.scopeValue("Error", {
        ref: validation_error_1.default,
        code: codegen_1._`require("ajv/dist/runtime/validation_error").default`
      });
    let validateName = gen.scopeName("validate");
    sch.validateName = validateName;
    let schemaCxt = {
      gen,
      allErrors: this.opts.allErrors,
      data: names_1.default.data,
      parentData: names_1.default.parentData,
      parentDataProperty: names_1.default.parentDataProperty,
      dataNames: [names_1.default.data],
      dataPathArr: [codegen_1.nil],
      dataLevel: 0,
      dataTypes: [],
      definedProperties: /* @__PURE__ */ new Set,
      topSchemaRef: gen.scopeValue("schema", this.opts.code.source === !0 ? { ref: sch.schema, code: codegen_1.stringify(sch.schema) } : { ref: sch.schema }),
      validateName,
      ValidationError: _ValidationError,
      schema: sch.schema,
      schemaEnv: sch,
      rootId,
      baseId: sch.baseId || rootId,
      schemaPath: codegen_1.nil,
      errSchemaPath: sch.schemaPath || (this.opts.jtd ? "" : "#"),
      errorPath: codegen_1._`""`,
      opts: this.opts,
      self: this
    }, sourceCode;
    try {
      this._compilations.add(sch), validate_1.validateFunctionCode(schemaCxt), gen.optimize(this.opts.code.optimize);
      let validateCode = gen.toString();
      if (sourceCode = `${gen.scopeRefs(names_1.default.scope)}return ${validateCode}`, this.opts.code.process)
        sourceCode = this.opts.code.process(sourceCode, sch);
      let validate = Function(`${names_1.default.self}`, `${names_1.default.scope}`, sourceCode)(this, this.scope.get());
      if (this.scope.value(validateName, { ref: validate }), validate.errors = null, validate.schema = sch.schema, validate.schemaEnv = sch, sch.$async)
        validate.$async = !0;
      if (this.opts.code.source === !0)
        validate.source = { validateName, validateCode, scopeValues: gen._values };
      if (this.opts.unevaluated) {
        let { props, items } = schemaCxt;
        if (validate.evaluated = {
          props: props instanceof codegen_1.Name ? void 0 : props,
          items: items instanceof codegen_1.Name ? void 0 : items,
          dynamicProps: props instanceof codegen_1.Name,
          dynamicItems: items instanceof codegen_1.Name
        }, validate.source)
          validate.source.evaluated = codegen_1.stringify(validate.evaluated);
      }
      return sch.validate = validate, sch;
    } catch (e) {
      if (delete sch.validate, delete sch.validateName, sourceCode)
        this.logger.error("Error compiling schema, function code:", sourceCode);
      throw e;
    } finally {
      this._compilations.delete(sch);
    }
  }
  exports.compileSchema = compileSchema;
  function resolveRef(root, baseId, ref) {
    var _a;
    ref = resolve_1.resolveUrl(this.opts.uriResolver, baseId, ref);
    let schOrFunc = root.refs[ref];
    if (schOrFunc)
      return schOrFunc;
    let _sch = resolve.call(this, root, ref);
    if (_sch === void 0) {
      let schema = (_a = root.localRefs) === null || _a === void 0 ? void 0 : _a[ref], { schemaId } = this.opts;
      if (schema)
        _sch = new SchemaEnv({ schema, schemaId, root, baseId });
    }
    if (_sch === void 0)
      return;
    return root.refs[ref] = inlineOrCompile.call(this, _sch);
  }
  exports.resolveRef = resolveRef;
  function inlineOrCompile(sch) {
    if (resolve_1.inlineRef(sch.schema, this.opts.inlineRefs))
      return sch.schema;
    return sch.validate ? sch : compileSchema.call(this, sch);
  }
  function getCompilingSchema(schEnv) {
    for (let sch of this._compilations)
      if (sameSchemaEnv(sch, schEnv))
        return sch;
  }
  exports.getCompilingSchema = getCompilingSchema;
  function sameSchemaEnv(s1, s2) {
    return s1.schema === s2.schema && s1.root === s2.root && s1.baseId === s2.baseId;
  }
  function resolve(root, ref) {
    let sch;
    while (typeof (sch = this.refs[ref]) == "string")
      ref = sch;
    return sch || this.schemas[ref] || resolveSchema.call(this, root, ref);
  }
  function resolveSchema(root, ref) {
    let p = this.opts.uriResolver.parse(ref), refPath = resolve_1._getFullPath(this.opts.uriResolver, p), baseId = resolve_1.getFullPath(this.opts.uriResolver, root.baseId, void 0);
    if (Object.keys(root.schema).length > 0 && refPath === baseId)
      return getJsonPointer.call(this, p, root);
    let id = resolve_1.normalizeId(refPath), schOrRef = this.refs[id] || this.schemas[id];
    if (typeof schOrRef == "string") {
      let sch = resolveSchema.call(this, root, schOrRef);
      if (typeof (sch === null || sch === void 0 ? void 0 : sch.schema) !== "object")
        return;
      return getJsonPointer.call(this, p, sch);
    }
    if (typeof (schOrRef === null || schOrRef === void 0 ? void 0 : schOrRef.schema) !== "object")
      return;
    if (!schOrRef.validate)
      compileSchema.call(this, schOrRef);
    if (id === resolve_1.normalizeId(ref)) {
      let { schema } = schOrRef, { schemaId } = this.opts, schId = schema[schemaId];
      if (schId)
        baseId = resolve_1.resolveUrl(this.opts.uriResolver, baseId, schId);
      return new SchemaEnv({ schema, schemaId, root, baseId });
    }
    return getJsonPointer.call(this, p, schOrRef);
  }
  exports.resolveSchema = resolveSchema;
  var PREVENT_SCOPE_CHANGE = /* @__PURE__ */ new Set([
    "properties",
    "patternProperties",
    "enum",
    "dependencies",
    "definitions"
  ]);
  function getJsonPointer(parsedRef, { baseId, schema, root }) {
    var _a;
    if (((_a = parsedRef.fragment) === null || _a === void 0 ? void 0 : _a[0]) !== "/")
      return;
    for (let part of parsedRef.fragment.slice(1).split("/")) {
      if (typeof schema === "boolean")
        return;
      let partSchema = schema[util_1.unescapeFragment(part)];
      if (partSchema === void 0)
        return;
      schema = partSchema;
      let schId = typeof schema === "object" && schema[this.opts.schemaId];
      if (!PREVENT_SCOPE_CHANGE.has(part) && schId)
        baseId = resolve_1.resolveUrl(this.opts.uriResolver, baseId, schId);
    }
    let env;
    if (typeof schema != "boolean" && schema.$ref && !util_1.schemaHasRulesButRef(schema, this.RULES)) {
      let $ref = resolve_1.resolveUrl(this.opts.uriResolver, baseId, schema.$ref);
      env = resolveSchema.call(this, root, $ref);
    }
    let { schemaId } = this.opts;
    if (env = env || new SchemaEnv({ schema, schemaId, root, baseId }), env.schema !== env.root.schema)
      return env;
    return;
  }
});

// node_modules/ajv/dist/refs/data.json
var require_data = __commonJS(function(exports, module) {
  module.exports = {
    $id: "https://raw.githubusercontent.com/ajv-validator/ajv/master/lib/refs/data.json#",
    description: "Meta-schema for $data reference (JSON AnySchema extension proposal)",
    type: "object",
    required: ["$data"],
    properties: {
      $data: {
        type: "string",
        anyOf: [{ format: "relative-json-pointer" }, { format: "json-pointer" }]
      }
    },
    additionalProperties: !1
  };
});

// node_modules/fast-uri/lib/utils.js
var require_utils = __commonJS(function(exports, module) {
  var isUUID = RegExp.prototype.test.bind(/^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/iu), isIPv4 = RegExp.prototype.test.bind(/^(?:(?:25[0-5]|2[0-4]\d|1\d{2}|[1-9]\d|\d)\.){3}(?:25[0-5]|2[0-4]\d|1\d{2}|[1-9]\d|\d)$/u);
  function stringArrayToHexStripped(input) {
    let acc = "", code = 0, i = 0;
    for (i = 0;i < input.length; i++) {
      if (code = input[i].charCodeAt(0), code === 48)
        continue;
      if (!(code >= 48 && code <= 57 || code >= 65 && code <= 70 || code >= 97 && code <= 102))
        return "";
      acc += input[i];
      break;
    }
    for (i += 1;i < input.length; i++) {
      if (code = input[i].charCodeAt(0), !(code >= 48 && code <= 57 || code >= 65 && code <= 70 || code >= 97 && code <= 102))
        return "";
      acc += input[i];
    }
    return acc;
  }
  var nonSimpleDomain = RegExp.prototype.test.bind(/[^!"$&'()*+,\-.;=_`a-z{}~]/u);
  function consumeIsZone(buffer) {
    return buffer.length = 0, !0;
  }
  function consumeHextets(buffer, address, output) {
    if (buffer.length) {
      let hex = stringArrayToHexStripped(buffer);
      if (hex !== "")
        address.push(hex);
      else
        return output.error = !0, !1;
      buffer.length = 0;
    }
    return !0;
  }
  function getIPV6(input) {
    let tokenCount = 0, output = { error: !1, address: "", zone: "" }, address = [], buffer = [], endipv6Encountered = !1, endIpv6 = !1, consume = consumeHextets;
    for (let i = 0;i < input.length; i++) {
      let cursor = input[i];
      if (cursor === "[" || cursor === "]")
        continue;
      if (cursor === ":") {
        if (endipv6Encountered === !0)
          endIpv6 = !0;
        if (!consume(buffer, address, output))
          break;
        if (++tokenCount > 7) {
          output.error = !0;
          break;
        }
        if (i > 0 && input[i - 1] === ":")
          endipv6Encountered = !0;
        address.push(":");
        continue;
      } else if (cursor === "%") {
        if (!consume(buffer, address, output))
          break;
        consume = consumeIsZone;
      } else {
        buffer.push(cursor);
        continue;
      }
    }
    if (buffer.length)
      if (consume === consumeIsZone)
        output.zone = buffer.join("");
      else if (endIpv6)
        address.push(buffer.join(""));
      else
        address.push(stringArrayToHexStripped(buffer));
    return output.address = address.join(""), output;
  }
  function normalizeIPv6(host) {
    if (findToken(host, ":") < 2)
      return { host, isIPV6: !1 };
    let ipv6 = getIPV6(host);
    if (!ipv6.error) {
      let { address: newHost, address: escapedHost } = ipv6;
      if (ipv6.zone)
        newHost += "%" + ipv6.zone, escapedHost += "%25" + ipv6.zone;
      return { host: newHost, isIPV6: !0, escapedHost };
    } else
      return { host, isIPV6: !1 };
  }
  function findToken(str, token) {
    let ind = 0;
    for (let i = 0;i < str.length; i++)
      if (str[i] === token)
        ind++;
    return ind;
  }
  function removeDotSegments(path) {
    let input = path, output = [], nextSlash = -1, len = 0;
    while (len = input.length) {
      if (len === 1)
        if (input === ".")
          break;
        else if (input === "/") {
          output.push("/");
          break;
        } else {
          output.push(input);
          break;
        }
      else if (len === 2) {
        if (input[0] === ".") {
          if (input[1] === ".")
            break;
          else if (input[1] === "/") {
            input = input.slice(2);
            continue;
          }
        } else if (input[0] === "/") {
          if (input[1] === "." || input[1] === "/") {
            output.push("/");
            break;
          }
        }
      } else if (len === 3) {
        if (input === "/..") {
          if (output.length !== 0)
            output.pop();
          output.push("/");
          break;
        }
      }
      if (input[0] === ".") {
        if (input[1] === ".") {
          if (input[2] === "/") {
            input = input.slice(3);
            continue;
          }
        } else if (input[1] === "/") {
          input = input.slice(2);
          continue;
        }
      } else if (input[0] === "/") {
        if (input[1] === ".") {
          if (input[2] === "/") {
            input = input.slice(2);
            continue;
          } else if (input[2] === ".") {
            if (input[3] === "/") {
              if (input = input.slice(3), output.length !== 0)
                output.pop();
              continue;
            }
          }
        }
      }
      if ((nextSlash = input.indexOf("/", 1)) === -1) {
        output.push(input);
        break;
      } else
        output.push(input.slice(0, nextSlash)), input = input.slice(nextSlash);
    }
    return output.join("");
  }
  function normalizeComponentEncoding(component, esc) {
    let func = esc !== !0 ? escape : unescape;
    if (component.scheme !== void 0)
      component.scheme = func(component.scheme);
    if (component.userinfo !== void 0)
      component.userinfo = func(component.userinfo);
    if (component.host !== void 0)
      component.host = func(component.host);
    if (component.path !== void 0)
      component.path = func(component.path);
    if (component.query !== void 0)
      component.query = func(component.query);
    if (component.fragment !== void 0)
      component.fragment = func(component.fragment);
    return component;
  }
  function recomposeAuthority(component) {
    let uriTokens = [];
    if (component.userinfo !== void 0)
      uriTokens.push(component.userinfo), uriTokens.push("@");
    if (component.host !== void 0) {
      let host = unescape(component.host);
      if (!isIPv4(host)) {
        let ipV6res = normalizeIPv6(host);
        if (ipV6res.isIPV6 === !0)
          host = `[${ipV6res.escapedHost}]`;
        else
          host = component.host;
      }
      uriTokens.push(host);
    }
    if (typeof component.port === "number" || typeof component.port === "string")
      uriTokens.push(":"), uriTokens.push(String(component.port));
    return uriTokens.length ? uriTokens.join("") : void 0;
  }
  module.exports = {
    nonSimpleDomain,
    recomposeAuthority,
    normalizeComponentEncoding,
    removeDotSegments,
    isIPv4,
    isUUID,
    normalizeIPv6,
    stringArrayToHexStripped
  };
});

// node_modules/fast-uri/lib/schemes.js
var require_schemes = __commonJS(function(exports, module) {
  var { isUUID } = require_utils(), URN_REG = /([\da-z][\d\-a-z]{0,31}):((?:[\w!$'()*+,\-.:;=@]|%[\da-f]{2})+)/iu, supportedSchemeNames = [
    "http",
    "https",
    "ws",
    "wss",
    "urn",
    "urn:uuid"
  ];
  function isValidSchemeName(name) {
    return supportedSchemeNames.indexOf(name) !== -1;
  }
  function wsIsSecure(wsComponent) {
    if (wsComponent.secure === !0)
      return !0;
    else if (wsComponent.secure === !1)
      return !1;
    else if (wsComponent.scheme)
      return wsComponent.scheme.length === 3 && (wsComponent.scheme[0] === "w" || wsComponent.scheme[0] === "W") && (wsComponent.scheme[1] === "s" || wsComponent.scheme[1] === "S") && (wsComponent.scheme[2] === "s" || wsComponent.scheme[2] === "S");
    else
      return !1;
  }
  function httpParse(component) {
    if (!component.host)
      component.error = component.error || "HTTP URIs must have a host.";
    return component;
  }
  function httpSerialize(component) {
    let secure = String(component.scheme).toLowerCase() === "https";
    if (component.port === (secure ? 443 : 80) || component.port === "")
      component.port = void 0;
    if (!component.path)
      component.path = "/";
    return component;
  }
  function wsParse(wsComponent) {
    return wsComponent.secure = wsIsSecure(wsComponent), wsComponent.resourceName = (wsComponent.path || "/") + (wsComponent.query ? "?" + wsComponent.query : ""), wsComponent.path = void 0, wsComponent.query = void 0, wsComponent;
  }
  function wsSerialize(wsComponent) {
    if (wsComponent.port === (wsIsSecure(wsComponent) ? 443 : 80) || wsComponent.port === "")
      wsComponent.port = void 0;
    if (typeof wsComponent.secure === "boolean")
      wsComponent.scheme = wsComponent.secure ? "wss" : "ws", wsComponent.secure = void 0;
    if (wsComponent.resourceName) {
      let [path, query] = wsComponent.resourceName.split("?");
      wsComponent.path = path && path !== "/" ? path : void 0, wsComponent.query = query, wsComponent.resourceName = void 0;
    }
    return wsComponent.fragment = void 0, wsComponent;
  }
  function urnParse(urnComponent, options) {
    if (!urnComponent.path)
      return urnComponent.error = "URN can not be parsed", urnComponent;
    let matches = urnComponent.path.match(URN_REG);
    if (matches) {
      let scheme = options.scheme || urnComponent.scheme || "urn";
      urnComponent.nid = matches[1].toLowerCase(), urnComponent.nss = matches[2];
      let urnScheme = `${scheme}:${options.nid || urnComponent.nid}`, schemeHandler = getSchemeHandler(urnScheme);
      if (urnComponent.path = void 0, schemeHandler)
        urnComponent = schemeHandler.parse(urnComponent, options);
    } else
      urnComponent.error = urnComponent.error || "URN can not be parsed.";
    return urnComponent;
  }
  function urnSerialize(urnComponent, options) {
    if (urnComponent.nid === void 0)
      throw Error("URN without nid cannot be serialized");
    let scheme = options.scheme || urnComponent.scheme || "urn", nid = urnComponent.nid.toLowerCase(), urnScheme = `${scheme}:${options.nid || nid}`, schemeHandler = getSchemeHandler(urnScheme);
    if (schemeHandler)
      urnComponent = schemeHandler.serialize(urnComponent, options);
    let uriComponent = urnComponent, nss = urnComponent.nss;
    return uriComponent.path = `${nid || options.nid}:${nss}`, options.skipEscape = !0, uriComponent;
  }
  function urnuuidParse(urnComponent, options) {
    let uuidComponent = urnComponent;
    if (uuidComponent.uuid = uuidComponent.nss, uuidComponent.nss = void 0, !options.tolerant && (!uuidComponent.uuid || !isUUID(uuidComponent.uuid)))
      uuidComponent.error = uuidComponent.error || "UUID is not valid.";
    return uuidComponent;
  }
  function urnuuidSerialize(uuidComponent) {
    let urnComponent = uuidComponent;
    return urnComponent.nss = (uuidComponent.uuid || "").toLowerCase(), urnComponent;
  }
  var http = {
    scheme: "http",
    domainHost: !0,
    parse: httpParse,
    serialize: httpSerialize
  }, https = {
    scheme: "https",
    domainHost: http.domainHost,
    parse: httpParse,
    serialize: httpSerialize
  }, ws = {
    scheme: "ws",
    domainHost: !0,
    parse: wsParse,
    serialize: wsSerialize
  }, wss = {
    scheme: "wss",
    domainHost: ws.domainHost,
    parse: ws.parse,
    serialize: ws.serialize
  }, urn = {
    scheme: "urn",
    parse: urnParse,
    serialize: urnSerialize,
    skipNormalize: !0
  }, urnuuid = {
    scheme: "urn:uuid",
    parse: urnuuidParse,
    serialize: urnuuidSerialize,
    skipNormalize: !0
  }, SCHEMES = {
    http,
    https,
    ws,
    wss,
    urn,
    "urn:uuid": urnuuid
  };
  Object.setPrototypeOf(SCHEMES, null);
  function getSchemeHandler(scheme) {
    return scheme && (SCHEMES[scheme] || SCHEMES[scheme.toLowerCase()]) || void 0;
  }
  module.exports = {
    wsIsSecure,
    SCHEMES,
    isValidSchemeName,
    getSchemeHandler
  };
});

// node_modules/fast-uri/index.js
var require_fast_uri = __commonJS(function(exports, module) {
  var { normalizeIPv6, removeDotSegments, recomposeAuthority, normalizeComponentEncoding, isIPv4, nonSimpleDomain } = require_utils(), { SCHEMES, getSchemeHandler } = require_schemes();
  function normalize(uri, options) {
    if (typeof uri === "string")
      uri = serialize(parse(uri, options), options);
    else if (typeof uri === "object")
      uri = parse(serialize(uri, options), options);
    return uri;
  }
  function resolve(baseURI, relativeURI, options) {
    let schemelessOptions = options ? Object.assign({ scheme: "null" }, options) : { scheme: "null" }, resolved = resolveComponent(parse(baseURI, schemelessOptions), parse(relativeURI, schemelessOptions), schemelessOptions, !0);
    return schemelessOptions.skipEscape = !0, serialize(resolved, schemelessOptions);
  }
  function resolveComponent(base, relative, options, skipNormalization) {
    let target = {};
    if (!skipNormalization)
      base = parse(serialize(base, options), options), relative = parse(serialize(relative, options), options);
    if (options = options || {}, !options.tolerant && relative.scheme)
      target.scheme = relative.scheme, target.userinfo = relative.userinfo, target.host = relative.host, target.port = relative.port, target.path = removeDotSegments(relative.path || ""), target.query = relative.query;
    else {
      if (relative.userinfo !== void 0 || relative.host !== void 0 || relative.port !== void 0)
        target.userinfo = relative.userinfo, target.host = relative.host, target.port = relative.port, target.path = removeDotSegments(relative.path || ""), target.query = relative.query;
      else {
        if (!relative.path)
          if (target.path = base.path, relative.query !== void 0)
            target.query = relative.query;
          else
            target.query = base.query;
        else {
          if (relative.path[0] === "/")
            target.path = removeDotSegments(relative.path);
          else {
            if ((base.userinfo !== void 0 || base.host !== void 0 || base.port !== void 0) && !base.path)
              target.path = "/" + relative.path;
            else if (!base.path)
              target.path = relative.path;
            else
              target.path = base.path.slice(0, base.path.lastIndexOf("/") + 1) + relative.path;
            target.path = removeDotSegments(target.path);
          }
          target.query = relative.query;
        }
        target.userinfo = base.userinfo, target.host = base.host, target.port = base.port;
      }
      target.scheme = base.scheme;
    }
    return target.fragment = relative.fragment, target;
  }
  function equal(uriA, uriB, options) {
    if (typeof uriA === "string")
      uriA = unescape(uriA), uriA = serialize(normalizeComponentEncoding(parse(uriA, options), !0), { ...options, skipEscape: !0 });
    else if (typeof uriA === "object")
      uriA = serialize(normalizeComponentEncoding(uriA, !0), { ...options, skipEscape: !0 });
    if (typeof uriB === "string")
      uriB = unescape(uriB), uriB = serialize(normalizeComponentEncoding(parse(uriB, options), !0), { ...options, skipEscape: !0 });
    else if (typeof uriB === "object")
      uriB = serialize(normalizeComponentEncoding(uriB, !0), { ...options, skipEscape: !0 });
    return uriA.toLowerCase() === uriB.toLowerCase();
  }
  function serialize(cmpts, opts) {
    let component = {
      host: cmpts.host,
      scheme: cmpts.scheme,
      userinfo: cmpts.userinfo,
      port: cmpts.port,
      path: cmpts.path,
      query: cmpts.query,
      nid: cmpts.nid,
      nss: cmpts.nss,
      uuid: cmpts.uuid,
      fragment: cmpts.fragment,
      reference: cmpts.reference,
      resourceName: cmpts.resourceName,
      secure: cmpts.secure,
      error: ""
    }, options = Object.assign({}, opts), uriTokens = [], schemeHandler = getSchemeHandler(options.scheme || component.scheme);
    if (schemeHandler && schemeHandler.serialize)
      schemeHandler.serialize(component, options);
    if (component.path !== void 0)
      if (!options.skipEscape) {
        if (component.path = escape(component.path), component.scheme !== void 0)
          component.path = component.path.split("%3A").join(":");
      } else
        component.path = unescape(component.path);
    if (options.reference !== "suffix" && component.scheme)
      uriTokens.push(component.scheme, ":");
    let authority = recomposeAuthority(component);
    if (authority !== void 0) {
      if (options.reference !== "suffix")
        uriTokens.push("//");
      if (uriTokens.push(authority), component.path && component.path[0] !== "/")
        uriTokens.push("/");
    }
    if (component.path !== void 0) {
      let s = component.path;
      if (!options.absolutePath && (!schemeHandler || !schemeHandler.absolutePath))
        s = removeDotSegments(s);
      if (authority === void 0 && s[0] === "/" && s[1] === "/")
        s = "/%2F" + s.slice(2);
      uriTokens.push(s);
    }
    if (component.query !== void 0)
      uriTokens.push("?", component.query);
    if (component.fragment !== void 0)
      uriTokens.push("#", component.fragment);
    return uriTokens.join("");
  }
  var URI_PARSE = /^(?:([^#/:?]+):)?(?:\/\/((?:([^#/?@]*)@)?(\[[^#/?\]]+\]|[^#/:?]*)(?::(\d*))?))?([^#?]*)(?:\?([^#]*))?(?:#((?:.|[\n\r])*))?/u;
  function parse(uri, opts) {
    let options = Object.assign({}, opts), parsed = {
      scheme: void 0,
      userinfo: void 0,
      host: "",
      port: void 0,
      path: "",
      query: void 0,
      fragment: void 0
    }, isIP = !1;
    if (options.reference === "suffix")
      if (options.scheme)
        uri = options.scheme + ":" + uri;
      else
        uri = "//" + uri;
    let matches = uri.match(URI_PARSE);
    if (matches) {
      if (parsed.scheme = matches[1], parsed.userinfo = matches[3], parsed.host = matches[4], parsed.port = parseInt(matches[5], 10), parsed.path = matches[6] || "", parsed.query = matches[7], parsed.fragment = matches[8], isNaN(parsed.port))
        parsed.port = matches[5];
      if (parsed.host)
        if (isIPv4(parsed.host) === !1) {
          let ipv6result = normalizeIPv6(parsed.host);
          parsed.host = ipv6result.host.toLowerCase(), isIP = ipv6result.isIPV6;
        } else
          isIP = !0;
      if (parsed.scheme === void 0 && parsed.userinfo === void 0 && parsed.host === void 0 && parsed.port === void 0 && parsed.query === void 0 && !parsed.path)
        parsed.reference = "same-document";
      else if (parsed.scheme === void 0)
        parsed.reference = "relative";
      else if (parsed.fragment === void 0)
        parsed.reference = "absolute";
      else
        parsed.reference = "uri";
      if (options.reference && options.reference !== "suffix" && options.reference !== parsed.reference)
        parsed.error = parsed.error || "URI is not a " + options.reference + " reference.";
      let schemeHandler = getSchemeHandler(options.scheme || parsed.scheme);
      if (!options.unicodeSupport && (!schemeHandler || !schemeHandler.unicodeSupport)) {
        if (parsed.host && (options.domainHost || schemeHandler && schemeHandler.domainHost) && isIP === !1 && nonSimpleDomain(parsed.host))
          try {
            parsed.host = URL.domainToASCII(parsed.host.toLowerCase());
          } catch (e) {
            parsed.error = parsed.error || "Host's domain name can not be converted to ASCII: " + e;
          }
      }
      if (!schemeHandler || schemeHandler && !schemeHandler.skipNormalize) {
        if (uri.indexOf("%") !== -1) {
          if (parsed.scheme !== void 0)
            parsed.scheme = unescape(parsed.scheme);
          if (parsed.host !== void 0)
            parsed.host = unescape(parsed.host);
        }
        if (parsed.path)
          parsed.path = escape(unescape(parsed.path));
        if (parsed.fragment)
          parsed.fragment = encodeURI(decodeURIComponent(parsed.fragment));
      }
      if (schemeHandler && schemeHandler.parse)
        schemeHandler.parse(parsed, options);
    } else
      parsed.error = parsed.error || "URI can not be parsed.";
    return parsed;
  }
  var fastUri = {
    SCHEMES,
    normalize,
    resolve,
    resolveComponent,
    equal,
    serialize,
    parse
  };
  module.exports = fastUri;
  module.exports.default = fastUri;
  module.exports.fastUri = fastUri;
});

// node_modules/ajv/dist/runtime/uri.js
var require_uri = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var uri = require_fast_uri();
  uri.code = 'require("ajv/dist/runtime/uri").default';
  exports.default = uri;
});

// node_modules/ajv/dist/core.js
var require_core = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.CodeGen = exports.Name = exports.nil = exports.stringify = exports.str = exports._ = exports.KeywordCxt = void 0;
  var validate_1 = require_validate();
  Object.defineProperty(exports, "KeywordCxt", { enumerable: !0, get: function() {
    return validate_1.KeywordCxt;
  } });
  var codegen_1 = require_codegen();
  Object.defineProperty(exports, "_", { enumerable: !0, get: function() {
    return codegen_1._;
  } });
  Object.defineProperty(exports, "str", { enumerable: !0, get: function() {
    return codegen_1.str;
  } });
  Object.defineProperty(exports, "stringify", { enumerable: !0, get: function() {
    return codegen_1.stringify;
  } });
  Object.defineProperty(exports, "nil", { enumerable: !0, get: function() {
    return codegen_1.nil;
  } });
  Object.defineProperty(exports, "Name", { enumerable: !0, get: function() {
    return codegen_1.Name;
  } });
  Object.defineProperty(exports, "CodeGen", { enumerable: !0, get: function() {
    return codegen_1.CodeGen;
  } });
  var validation_error_1 = require_validation_error(), ref_error_1 = require_ref_error(), rules_1 = require_rules(), compile_1 = require_compile(), codegen_2 = require_codegen(), resolve_1 = require_resolve(), dataType_1 = require_dataType(), util_1 = require_util(), $dataRefSchema = require_data(), uri_1 = require_uri(), defaultRegExp = (str, flags) => new RegExp(str, flags);
  defaultRegExp.code = "new RegExp";
  var META_IGNORE_OPTIONS = ["removeAdditional", "useDefaults", "coerceTypes"], EXT_SCOPE_NAMES = /* @__PURE__ */ new Set([
    "validate",
    "serialize",
    "parse",
    "wrapper",
    "root",
    "schema",
    "keyword",
    "pattern",
    "formats",
    "validate$data",
    "func",
    "obj",
    "Error"
  ]), removedOptions = {
    errorDataPath: "",
    format: "`validateFormats: false` can be used instead.",
    nullable: '"nullable" keyword is supported by default.',
    jsonPointers: "Deprecated jsPropertySyntax can be used instead.",
    extendRefs: "Deprecated ignoreKeywordsWithRef can be used instead.",
    missingRefs: "Pass empty schema with $id that should be ignored to ajv.addSchema.",
    processCode: "Use option `code: {process: (code, schemaEnv: object) => string}`",
    sourceCode: "Use option `code: {source: true}`",
    strictDefaults: "It is default now, see option `strict`.",
    strictKeywords: "It is default now, see option `strict`.",
    uniqueItems: '"uniqueItems" keyword is always validated.',
    unknownFormats: "Disable strict mode or pass `true` to `ajv.addFormat` (or `formats` option).",
    cache: "Map is used as cache, schema object as key.",
    serialize: "Map is used as cache, schema object as key.",
    ajvErrors: "It is default now."
  }, deprecatedOptions = {
    ignoreKeywordsWithRef: "",
    jsPropertySyntax: "",
    unicode: '"minLength"/"maxLength" account for unicode characters by default.'
  }, MAX_EXPRESSION = 200;
  function requiredOptions(o) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o, _p, _q, _r, _s, _t, _u, _v, _w, _x, _y, _z, _0;
    let s = o.strict, _optz = (_a = o.code) === null || _a === void 0 ? void 0 : _a.optimize, optimize = _optz === !0 || _optz === void 0 ? 1 : _optz || 0, regExp = (_c = (_b = o.code) === null || _b === void 0 ? void 0 : _b.regExp) !== null && _c !== void 0 ? _c : defaultRegExp, uriResolver = (_d = o.uriResolver) !== null && _d !== void 0 ? _d : uri_1.default;
    return {
      strictSchema: (_f = (_e = o.strictSchema) !== null && _e !== void 0 ? _e : s) !== null && _f !== void 0 ? _f : !0,
      strictNumbers: (_h = (_g = o.strictNumbers) !== null && _g !== void 0 ? _g : s) !== null && _h !== void 0 ? _h : !0,
      strictTypes: (_k = (_j = o.strictTypes) !== null && _j !== void 0 ? _j : s) !== null && _k !== void 0 ? _k : "log",
      strictTuples: (_m = (_l = o.strictTuples) !== null && _l !== void 0 ? _l : s) !== null && _m !== void 0 ? _m : "log",
      strictRequired: (_p = (_o = o.strictRequired) !== null && _o !== void 0 ? _o : s) !== null && _p !== void 0 ? _p : !1,
      code: o.code ? { ...o.code, optimize, regExp } : { optimize, regExp },
      loopRequired: (_q = o.loopRequired) !== null && _q !== void 0 ? _q : MAX_EXPRESSION,
      loopEnum: (_r = o.loopEnum) !== null && _r !== void 0 ? _r : MAX_EXPRESSION,
      meta: (_s = o.meta) !== null && _s !== void 0 ? _s : !0,
      messages: (_t = o.messages) !== null && _t !== void 0 ? _t : !0,
      inlineRefs: (_u = o.inlineRefs) !== null && _u !== void 0 ? _u : !0,
      schemaId: (_v = o.schemaId) !== null && _v !== void 0 ? _v : "$id",
      addUsedSchema: (_w = o.addUsedSchema) !== null && _w !== void 0 ? _w : !0,
      validateSchema: (_x = o.validateSchema) !== null && _x !== void 0 ? _x : !0,
      validateFormats: (_y = o.validateFormats) !== null && _y !== void 0 ? _y : !0,
      unicodeRegExp: (_z = o.unicodeRegExp) !== null && _z !== void 0 ? _z : !0,
      int32range: (_0 = o.int32range) !== null && _0 !== void 0 ? _0 : !0,
      uriResolver
    };
  }

  class Ajv {
    constructor(opts = {}) {
      this.schemas = {}, this.refs = {}, this.formats = {}, this._compilations = /* @__PURE__ */ new Set, this._loading = {}, this._cache = /* @__PURE__ */ new Map, opts = this.opts = { ...opts, ...requiredOptions(opts) };
      let { es5, lines } = this.opts.code;
      this.scope = new codegen_2.ValueScope({ scope: {}, prefixes: EXT_SCOPE_NAMES, es5, lines }), this.logger = getLogger(opts.logger);
      let formatOpt = opts.validateFormats;
      if (opts.validateFormats = !1, this.RULES = rules_1.getRules(), checkOptions.call(this, removedOptions, opts, "NOT SUPPORTED"), checkOptions.call(this, deprecatedOptions, opts, "DEPRECATED", "warn"), this._metaOpts = getMetaSchemaOptions.call(this), opts.formats)
        addInitialFormats.call(this);
      if (this._addVocabularies(), this._addDefaultMetaSchema(), opts.keywords)
        addInitialKeywords.call(this, opts.keywords);
      if (typeof opts.meta == "object")
        this.addMetaSchema(opts.meta);
      addInitialSchemas.call(this), opts.validateFormats = formatOpt;
    }
    _addVocabularies() {
      this.addKeyword("$async");
    }
    _addDefaultMetaSchema() {
      let { $data, meta, schemaId } = this.opts, _dataRefSchema = $dataRefSchema;
      if (schemaId === "id")
        _dataRefSchema = { ...$dataRefSchema }, _dataRefSchema.id = _dataRefSchema.$id, delete _dataRefSchema.$id;
      if (meta && $data)
        this.addMetaSchema(_dataRefSchema, _dataRefSchema[schemaId], !1);
    }
    defaultMeta() {
      let { meta, schemaId } = this.opts;
      return this.opts.defaultMeta = typeof meta == "object" ? meta[schemaId] || meta : void 0;
    }
    validate(schemaKeyRef, data) {
      let v;
      if (typeof schemaKeyRef == "string") {
        if (v = this.getSchema(schemaKeyRef), !v)
          throw Error(`no schema with key or ref "${schemaKeyRef}"`);
      } else
        v = this.compile(schemaKeyRef);
      let valid = v(data);
      if (!("$async" in v))
        this.errors = v.errors;
      return valid;
    }
    compile(schema, _meta) {
      let sch = this._addSchema(schema, _meta);
      return sch.validate || this._compileSchemaEnv(sch);
    }
    compileAsync(schema, meta) {
      if (typeof this.opts.loadSchema != "function")
        throw Error("options.loadSchema should be a function");
      let { loadSchema } = this.opts;
      return runCompileAsync.call(this, schema, meta);
      async function runCompileAsync(_schema, _meta) {
        await loadMetaSchema.call(this, _schema.$schema);
        let sch = this._addSchema(_schema, _meta);
        return sch.validate || _compileAsync.call(this, sch);
      }
      async function loadMetaSchema($ref) {
        if ($ref && !this.getSchema($ref))
          await runCompileAsync.call(this, { $ref }, !0);
      }
      async function _compileAsync(sch) {
        try {
          return this._compileSchemaEnv(sch);
        } catch (e) {
          if (!(e instanceof ref_error_1.default))
            throw e;
          return checkLoaded.call(this, e), await loadMissingSchema.call(this, e.missingSchema), _compileAsync.call(this, sch);
        }
      }
      function checkLoaded({ missingSchema: ref, missingRef }) {
        if (this.refs[ref])
          throw Error(`AnySchema ${ref} is loaded but ${missingRef} cannot be resolved`);
      }
      async function loadMissingSchema(ref) {
        let _schema = await _loadSchema.call(this, ref);
        if (!this.refs[ref])
          await loadMetaSchema.call(this, _schema.$schema);
        if (!this.refs[ref])
          this.addSchema(_schema, ref, meta);
      }
      async function _loadSchema(ref) {
        let p = this._loading[ref];
        if (p)
          return p;
        try {
          return await (this._loading[ref] = loadSchema(ref));
        } finally {
          delete this._loading[ref];
        }
      }
    }
    addSchema(schema, key, _meta, _validateSchema = this.opts.validateSchema) {
      if (Array.isArray(schema)) {
        for (let sch of schema)
          this.addSchema(sch, void 0, _meta, _validateSchema);
        return this;
      }
      let id;
      if (typeof schema === "object") {
        let { schemaId } = this.opts;
        if (id = schema[schemaId], id !== void 0 && typeof id != "string")
          throw Error(`schema ${schemaId} must be string`);
      }
      return key = resolve_1.normalizeId(key || id), this._checkUnique(key), this.schemas[key] = this._addSchema(schema, _meta, key, _validateSchema, !0), this;
    }
    addMetaSchema(schema, key, _validateSchema = this.opts.validateSchema) {
      return this.addSchema(schema, key, !0, _validateSchema), this;
    }
    validateSchema(schema, throwOrLogError) {
      if (typeof schema == "boolean")
        return !0;
      let $schema;
      if ($schema = schema.$schema, $schema !== void 0 && typeof $schema != "string")
        throw Error("$schema must be a string");
      if ($schema = $schema || this.opts.defaultMeta || this.defaultMeta(), !$schema)
        return this.logger.warn("meta-schema not available"), this.errors = null, !0;
      let valid = this.validate($schema, schema);
      if (!valid && throwOrLogError) {
        let message = "schema is invalid: " + this.errorsText();
        if (this.opts.validateSchema === "log")
          this.logger.error(message);
        else
          throw Error(message);
      }
      return valid;
    }
    getSchema(keyRef) {
      let sch;
      while (typeof (sch = getSchEnv.call(this, keyRef)) == "string")
        keyRef = sch;
      if (sch === void 0) {
        let { schemaId } = this.opts, root = new compile_1.SchemaEnv({ schema: {}, schemaId });
        if (sch = compile_1.resolveSchema.call(this, root, keyRef), !sch)
          return;
        this.refs[keyRef] = sch;
      }
      return sch.validate || this._compileSchemaEnv(sch);
    }
    removeSchema(schemaKeyRef) {
      if (schemaKeyRef instanceof RegExp)
        return this._removeAllSchemas(this.schemas, schemaKeyRef), this._removeAllSchemas(this.refs, schemaKeyRef), this;
      switch (typeof schemaKeyRef) {
        case "undefined":
          return this._removeAllSchemas(this.schemas), this._removeAllSchemas(this.refs), this._cache.clear(), this;
        case "string": {
          let sch = getSchEnv.call(this, schemaKeyRef);
          if (typeof sch == "object")
            this._cache.delete(sch.schema);
          return delete this.schemas[schemaKeyRef], delete this.refs[schemaKeyRef], this;
        }
        case "object": {
          let cacheKey = schemaKeyRef;
          this._cache.delete(cacheKey);
          let id = schemaKeyRef[this.opts.schemaId];
          if (id)
            id = resolve_1.normalizeId(id), delete this.schemas[id], delete this.refs[id];
          return this;
        }
        default:
          throw Error("ajv.removeSchema: invalid parameter");
      }
    }
    addVocabulary(definitions) {
      for (let def of definitions)
        this.addKeyword(def);
      return this;
    }
    addKeyword(kwdOrDef, def) {
      let keyword;
      if (typeof kwdOrDef == "string") {
        if (keyword = kwdOrDef, typeof def == "object")
          this.logger.warn("these parameters are deprecated, see docs for addKeyword"), def.keyword = keyword;
      } else if (typeof kwdOrDef == "object" && def === void 0) {
        if (def = kwdOrDef, keyword = def.keyword, Array.isArray(keyword) && !keyword.length)
          throw Error("addKeywords: keyword must be string or non-empty array");
      } else
        throw Error("invalid addKeywords parameters");
      if (checkKeyword.call(this, keyword, def), !def)
        return util_1.eachItem(keyword, (kwd) => addRule.call(this, kwd)), this;
      keywordMetaschema.call(this, def);
      let definition = {
        ...def,
        type: dataType_1.getJSONTypes(def.type),
        schemaType: dataType_1.getJSONTypes(def.schemaType)
      };
      return util_1.eachItem(keyword, definition.type.length === 0 ? (k) => addRule.call(this, k, definition) : (k) => definition.type.forEach((t) => addRule.call(this, k, definition, t))), this;
    }
    getKeyword(keyword) {
      let rule = this.RULES.all[keyword];
      return typeof rule == "object" ? rule.definition : !!rule;
    }
    removeKeyword(keyword) {
      let { RULES } = this;
      delete RULES.keywords[keyword], delete RULES.all[keyword];
      for (let group of RULES.rules) {
        let i = group.rules.findIndex((rule) => rule.keyword === keyword);
        if (i >= 0)
          group.rules.splice(i, 1);
      }
      return this;
    }
    addFormat(name, format) {
      if (typeof format == "string")
        format = new RegExp(format);
      return this.formats[name] = format, this;
    }
    errorsText(errors = this.errors, { separator = ", ", dataVar = "data" } = {}) {
      if (!errors || errors.length === 0)
        return "No errors";
      return errors.map((e) => `${dataVar}${e.instancePath} ${e.message}`).reduce((text, msg) => text + separator + msg);
    }
    $dataMetaSchema(metaSchema, keywordsJsonPointers) {
      let rules = this.RULES.all;
      metaSchema = JSON.parse(JSON.stringify(metaSchema));
      for (let jsonPointer of keywordsJsonPointers) {
        let segments = jsonPointer.split("/").slice(1), keywords = metaSchema;
        for (let seg of segments)
          keywords = keywords[seg];
        for (let key in rules) {
          let rule = rules[key];
          if (typeof rule != "object")
            continue;
          let { $data } = rule.definition, schema = keywords[key];
          if ($data && schema)
            keywords[key] = schemaOrData(schema);
        }
      }
      return metaSchema;
    }
    _removeAllSchemas(schemas, regex) {
      for (let keyRef in schemas) {
        let sch = schemas[keyRef];
        if (!regex || regex.test(keyRef)) {
          if (typeof sch == "string")
            delete schemas[keyRef];
          else if (sch && !sch.meta)
            this._cache.delete(sch.schema), delete schemas[keyRef];
        }
      }
    }
    _addSchema(schema, meta, baseId, validateSchema = this.opts.validateSchema, addSchema = this.opts.addUsedSchema) {
      let id, { schemaId } = this.opts;
      if (typeof schema == "object")
        id = schema[schemaId];
      else if (this.opts.jtd)
        throw Error("schema must be object");
      else if (typeof schema != "boolean")
        throw Error("schema must be object or boolean");
      let sch = this._cache.get(schema);
      if (sch !== void 0)
        return sch;
      baseId = resolve_1.normalizeId(id || baseId);
      let localRefs = resolve_1.getSchemaRefs.call(this, schema, baseId);
      if (sch = new compile_1.SchemaEnv({ schema, schemaId, meta, baseId, localRefs }), this._cache.set(sch.schema, sch), addSchema && !baseId.startsWith("#")) {
        if (baseId)
          this._checkUnique(baseId);
        this.refs[baseId] = sch;
      }
      if (validateSchema)
        this.validateSchema(schema, !0);
      return sch;
    }
    _checkUnique(id) {
      if (this.schemas[id] || this.refs[id])
        throw Error(`schema with key or id "${id}" already exists`);
    }
    _compileSchemaEnv(sch) {
      if (sch.meta)
        this._compileMetaSchema(sch);
      else
        compile_1.compileSchema.call(this, sch);
      if (!sch.validate)
        throw Error("ajv implementation error");
      return sch.validate;
    }
    _compileMetaSchema(sch) {
      let currentOpts = this.opts;
      this.opts = this._metaOpts;
      try {
        compile_1.compileSchema.call(this, sch);
      } finally {
        this.opts = currentOpts;
      }
    }
  }
  Ajv.ValidationError = validation_error_1.default;
  Ajv.MissingRefError = ref_error_1.default;
  exports.default = Ajv;
  function checkOptions(checkOpts, options, msg, log = "error") {
    for (let key in checkOpts) {
      let opt = key;
      if (opt in options)
        this.logger[log](`${msg}: option ${key}. ${checkOpts[opt]}`);
    }
  }
  function getSchEnv(keyRef) {
    return keyRef = resolve_1.normalizeId(keyRef), this.schemas[keyRef] || this.refs[keyRef];
  }
  function addInitialSchemas() {
    let optsSchemas = this.opts.schemas;
    if (!optsSchemas)
      return;
    if (Array.isArray(optsSchemas))
      this.addSchema(optsSchemas);
    else
      for (let key in optsSchemas)
        this.addSchema(optsSchemas[key], key);
  }
  function addInitialFormats() {
    for (let name in this.opts.formats) {
      let format = this.opts.formats[name];
      if (format)
        this.addFormat(name, format);
    }
  }
  function addInitialKeywords(defs) {
    if (Array.isArray(defs)) {
      this.addVocabulary(defs);
      return;
    }
    this.logger.warn("keywords option as map is deprecated, pass array");
    for (let keyword in defs) {
      let def = defs[keyword];
      if (!def.keyword)
        def.keyword = keyword;
      this.addKeyword(def);
    }
  }
  function getMetaSchemaOptions() {
    let metaOpts = { ...this.opts };
    for (let opt of META_IGNORE_OPTIONS)
      delete metaOpts[opt];
    return metaOpts;
  }
  var noLogs = { log() {}, warn() {}, error() {} };
  function getLogger(logger) {
    if (logger === !1)
      return noLogs;
    if (logger === void 0)
      return console;
    if (logger.log && logger.warn && logger.error)
      return logger;
    throw Error("logger must implement log, warn and error methods");
  }
  var KEYWORD_NAME = /^[a-z_$][a-z0-9_$:-]*$/i;
  function checkKeyword(keyword, def) {
    let { RULES } = this;
    if (util_1.eachItem(keyword, (kwd) => {
      if (RULES.keywords[kwd])
        throw Error(`Keyword ${kwd} is already defined`);
      if (!KEYWORD_NAME.test(kwd))
        throw Error(`Keyword ${kwd} has invalid name`);
    }), !def)
      return;
    if (def.$data && !(("code" in def) || ("validate" in def)))
      throw Error('$data keyword must have "code" or "validate" function');
  }
  function addRule(keyword, definition, dataType) {
    var _a;
    let post = definition === null || definition === void 0 ? void 0 : definition.post;
    if (dataType && post)
      throw Error('keyword with "post" flag cannot have "type"');
    let { RULES } = this, ruleGroup = post ? RULES.post : RULES.rules.find(({ type: t }) => t === dataType);
    if (!ruleGroup)
      ruleGroup = { type: dataType, rules: [] }, RULES.rules.push(ruleGroup);
    if (RULES.keywords[keyword] = !0, !definition)
      return;
    let rule = {
      keyword,
      definition: {
        ...definition,
        type: dataType_1.getJSONTypes(definition.type),
        schemaType: dataType_1.getJSONTypes(definition.schemaType)
      }
    };
    if (definition.before)
      addBeforeRule.call(this, ruleGroup, rule, definition.before);
    else
      ruleGroup.rules.push(rule);
    RULES.all[keyword] = rule, (_a = definition.implements) === null || _a === void 0 || _a.forEach((kwd) => this.addKeyword(kwd));
  }
  function addBeforeRule(ruleGroup, rule, before) {
    let i = ruleGroup.rules.findIndex((_rule) => _rule.keyword === before);
    if (i >= 0)
      ruleGroup.rules.splice(i, 0, rule);
    else
      ruleGroup.rules.push(rule), this.logger.warn(`rule ${before} is not defined`);
  }
  function keywordMetaschema(def) {
    let { metaSchema } = def;
    if (metaSchema === void 0)
      return;
    if (def.$data && this.opts.$data)
      metaSchema = schemaOrData(metaSchema);
    def.validateSchema = this.compile(metaSchema, !0);
  }
  var $dataRef = {
    $ref: "https://raw.githubusercontent.com/ajv-validator/ajv/master/lib/refs/data.json#"
  };
  function schemaOrData(schema) {
    return { anyOf: [schema, $dataRef] };
  }
});

// node_modules/ajv/dist/vocabularies/core/id.js
var require_id = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var def = {
    keyword: "id",
    code() {
      throw Error('NOT SUPPORTED: keyword "id", use "$id" for schema ID');
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/core/ref.js
var require_ref = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.callRef = exports.getValidate = void 0;
  var ref_error_1 = require_ref_error(), code_1 = require_code2(), codegen_1 = require_codegen(), names_1 = require_names(), compile_1 = require_compile(), util_1 = require_util(), def = {
    keyword: "$ref",
    schemaType: "string",
    code(cxt) {
      let { gen, schema: $ref, it } = cxt, { baseId, schemaEnv: env, validateName, opts, self } = it, { root } = env;
      if (($ref === "#" || $ref === "#/") && baseId === root.baseId)
        return callRootRef();
      let schOrEnv = compile_1.resolveRef.call(self, root, baseId, $ref);
      if (schOrEnv === void 0)
        throw new ref_error_1.default(it.opts.uriResolver, baseId, $ref);
      if (schOrEnv instanceof compile_1.SchemaEnv)
        return callValidate(schOrEnv);
      return inlineRefSchema(schOrEnv);
      function callRootRef() {
        if (env === root)
          return callRef(cxt, validateName, env, env.$async);
        let rootName = gen.scopeValue("root", { ref: root });
        return callRef(cxt, codegen_1._`${rootName}.validate`, root, root.$async);
      }
      function callValidate(sch) {
        let v = getValidate(cxt, sch);
        callRef(cxt, v, sch, sch.$async);
      }
      function inlineRefSchema(sch) {
        let schName = gen.scopeValue("schema", opts.code.source === !0 ? { ref: sch, code: codegen_1.stringify(sch) } : { ref: sch }), valid = gen.name("valid"), schCxt = cxt.subschema({
          schema: sch,
          dataTypes: [],
          schemaPath: codegen_1.nil,
          topSchemaRef: schName,
          errSchemaPath: $ref
        }, valid);
        cxt.mergeEvaluated(schCxt), cxt.ok(valid);
      }
    }
  };
  function getValidate(cxt, sch) {
    let { gen } = cxt;
    return sch.validate ? gen.scopeValue("validate", { ref: sch.validate }) : codegen_1._`${gen.scopeValue("wrapper", { ref: sch })}.validate`;
  }
  exports.getValidate = getValidate;
  function callRef(cxt, v, sch, $async) {
    let { gen, it } = cxt, { allErrors, schemaEnv: env, opts } = it, passCxt = opts.passContext ? names_1.default.this : codegen_1.nil;
    if ($async)
      callAsyncRef();
    else
      callSyncRef();
    function callAsyncRef() {
      if (!env.$async)
        throw Error("async schema referenced by sync schema");
      let valid = gen.let("valid");
      gen.try(() => {
        if (gen.code(codegen_1._`await ${code_1.callValidateCode(cxt, v, passCxt)}`), addEvaluatedFrom(v), !allErrors)
          gen.assign(valid, !0);
      }, (e) => {
        if (gen.if(codegen_1._`!(${e} instanceof ${it.ValidationError})`, () => gen.throw(e)), addErrorsFrom(e), !allErrors)
          gen.assign(valid, !1);
      }), cxt.ok(valid);
    }
    function callSyncRef() {
      cxt.result(code_1.callValidateCode(cxt, v, passCxt), () => addEvaluatedFrom(v), () => addErrorsFrom(v));
    }
    function addErrorsFrom(source) {
      let errs = codegen_1._`${source}.errors`;
      gen.assign(names_1.default.vErrors, codegen_1._`${names_1.default.vErrors} === null ? ${errs} : ${names_1.default.vErrors}.concat(${errs})`), gen.assign(names_1.default.errors, codegen_1._`${names_1.default.vErrors}.length`);
    }
    function addEvaluatedFrom(source) {
      var _a;
      if (!it.opts.unevaluated)
        return;
      let schEvaluated = (_a = sch === null || sch === void 0 ? void 0 : sch.validate) === null || _a === void 0 ? void 0 : _a.evaluated;
      if (it.props !== !0)
        if (schEvaluated && !schEvaluated.dynamicProps) {
          if (schEvaluated.props !== void 0)
            it.props = util_1.mergeEvaluated.props(gen, schEvaluated.props, it.props);
        } else {
          let props = gen.var("props", codegen_1._`${source}.evaluated.props`);
          it.props = util_1.mergeEvaluated.props(gen, props, it.props, codegen_1.Name);
        }
      if (it.items !== !0)
        if (schEvaluated && !schEvaluated.dynamicItems) {
          if (schEvaluated.items !== void 0)
            it.items = util_1.mergeEvaluated.items(gen, schEvaluated.items, it.items);
        } else {
          let items = gen.var("items", codegen_1._`${source}.evaluated.items`);
          it.items = util_1.mergeEvaluated.items(gen, items, it.items, codegen_1.Name);
        }
    }
  }
  exports.callRef = callRef;
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/core/index.js
var require_core2 = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var id_1 = require_id(), ref_1 = require_ref(), core = [
    "$schema",
    "$id",
    "$defs",
    "$vocabulary",
    { keyword: "$comment" },
    "definitions",
    id_1.default,
    ref_1.default
  ];
  exports.default = core;
});

// node_modules/ajv/dist/vocabularies/validation/limitNumber.js
var require_limitNumber = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var codegen_1 = require_codegen(), ops = codegen_1.operators, KWDs = {
    maximum: { okStr: "<=", ok: ops.LTE, fail: ops.GT },
    minimum: { okStr: ">=", ok: ops.GTE, fail: ops.LT },
    exclusiveMaximum: { okStr: "<", ok: ops.LT, fail: ops.GTE },
    exclusiveMinimum: { okStr: ">", ok: ops.GT, fail: ops.LTE }
  }, error = {
    message: ({ keyword, schemaCode }) => codegen_1.str`must be ${KWDs[keyword].okStr} ${schemaCode}`,
    params: ({ keyword, schemaCode }) => codegen_1._`{comparison: ${KWDs[keyword].okStr}, limit: ${schemaCode}}`
  }, def = {
    keyword: Object.keys(KWDs),
    type: "number",
    schemaType: "number",
    $data: !0,
    error,
    code(cxt) {
      let { keyword, data, schemaCode } = cxt;
      cxt.fail$data(codegen_1._`${data} ${KWDs[keyword].fail} ${schemaCode} || isNaN(${data})`);
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/validation/multipleOf.js
var require_multipleOf = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var codegen_1 = require_codegen(), error = {
    message: ({ schemaCode }) => codegen_1.str`must be multiple of ${schemaCode}`,
    params: ({ schemaCode }) => codegen_1._`{multipleOf: ${schemaCode}}`
  }, def = {
    keyword: "multipleOf",
    type: "number",
    schemaType: "number",
    $data: !0,
    error,
    code(cxt) {
      let { gen, data, schemaCode, it } = cxt, prec = it.opts.multipleOfPrecision, res = gen.let("res"), invalid = prec ? codegen_1._`Math.abs(Math.round(${res}) - ${res}) > 1e-${prec}` : codegen_1._`${res} !== parseInt(${res})`;
      cxt.fail$data(codegen_1._`(${schemaCode} === 0 || (${res} = ${data}/${schemaCode}, ${invalid}))`);
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/runtime/ucs2length.js
var require_ucs2length = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  function ucs2length(str) {
    let len = str.length, length = 0, pos = 0, value;
    while (pos < len)
      if (length++, value = str.charCodeAt(pos++), value >= 55296 && value <= 56319 && pos < len) {
        if (value = str.charCodeAt(pos), (value & 64512) === 56320)
          pos++;
      }
    return length;
  }
  exports.default = ucs2length;
  ucs2length.code = 'require("ajv/dist/runtime/ucs2length").default';
});

// node_modules/ajv/dist/vocabularies/validation/limitLength.js
var require_limitLength = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var codegen_1 = require_codegen(), util_1 = require_util(), ucs2length_1 = require_ucs2length(), error = {
    message({ keyword, schemaCode }) {
      return codegen_1.str`must NOT have ${keyword === "maxLength" ? "more" : "fewer"} than ${schemaCode} characters`;
    },
    params: ({ schemaCode }) => codegen_1._`{limit: ${schemaCode}}`
  }, def = {
    keyword: ["maxLength", "minLength"],
    type: "string",
    schemaType: "number",
    $data: !0,
    error,
    code(cxt) {
      let { keyword, data, schemaCode, it } = cxt, op = keyword === "maxLength" ? codegen_1.operators.GT : codegen_1.operators.LT, len = it.opts.unicode === !1 ? codegen_1._`${data}.length` : codegen_1._`${util_1.useFunc(cxt.gen, ucs2length_1.default)}(${data})`;
      cxt.fail$data(codegen_1._`${len} ${op} ${schemaCode}`);
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/validation/pattern.js
var require_pattern = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var code_1 = require_code2(), codegen_1 = require_codegen(), error = {
    message: ({ schemaCode }) => codegen_1.str`must match pattern "${schemaCode}"`,
    params: ({ schemaCode }) => codegen_1._`{pattern: ${schemaCode}}`
  }, def = {
    keyword: "pattern",
    type: "string",
    schemaType: "string",
    $data: !0,
    error,
    code(cxt) {
      let { data, $data, schema, schemaCode, it } = cxt, u = it.opts.unicodeRegExp ? "u" : "", regExp = $data ? codegen_1._`(new RegExp(${schemaCode}, ${u}))` : code_1.usePattern(cxt, schema);
      cxt.fail$data(codegen_1._`!${regExp}.test(${data})`);
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/validation/limitProperties.js
var require_limitProperties = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var codegen_1 = require_codegen(), error = {
    message({ keyword, schemaCode }) {
      return codegen_1.str`must NOT have ${keyword === "maxProperties" ? "more" : "fewer"} than ${schemaCode} properties`;
    },
    params: ({ schemaCode }) => codegen_1._`{limit: ${schemaCode}}`
  }, def = {
    keyword: ["maxProperties", "minProperties"],
    type: "object",
    schemaType: "number",
    $data: !0,
    error,
    code(cxt) {
      let { keyword, data, schemaCode } = cxt, op = keyword === "maxProperties" ? codegen_1.operators.GT : codegen_1.operators.LT;
      cxt.fail$data(codegen_1._`Object.keys(${data}).length ${op} ${schemaCode}`);
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/validation/required.js
var require_required = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var code_1 = require_code2(), codegen_1 = require_codegen(), util_1 = require_util(), error = {
    message: ({ params: { missingProperty } }) => codegen_1.str`must have required property '${missingProperty}'`,
    params: ({ params: { missingProperty } }) => codegen_1._`{missingProperty: ${missingProperty}}`
  }, def = {
    keyword: "required",
    type: "object",
    schemaType: "array",
    $data: !0,
    error,
    code(cxt) {
      let { gen, schema, schemaCode, data, $data, it } = cxt, { opts } = it;
      if (!$data && schema.length === 0)
        return;
      let useLoop = schema.length >= opts.loopRequired;
      if (it.allErrors)
        allErrorsMode();
      else
        exitOnErrorMode();
      if (opts.strictRequired) {
        let props = cxt.parentSchema.properties, { definedProperties } = cxt.it;
        for (let requiredKey of schema)
          if ((props === null || props === void 0 ? void 0 : props[requiredKey]) === void 0 && !definedProperties.has(requiredKey)) {
            let schemaPath = it.schemaEnv.baseId + it.errSchemaPath, msg = `required property "${requiredKey}" is not defined at "${schemaPath}" (strictRequired)`;
            util_1.checkStrictMode(it, msg, it.opts.strictRequired);
          }
      }
      function allErrorsMode() {
        if (useLoop || $data)
          cxt.block$data(codegen_1.nil, loopAllRequired);
        else
          for (let prop of schema)
            code_1.checkReportMissingProp(cxt, prop);
      }
      function exitOnErrorMode() {
        let missing = gen.let("missing");
        if (useLoop || $data) {
          let valid = gen.let("valid", !0);
          cxt.block$data(valid, () => loopUntilMissing(missing, valid)), cxt.ok(valid);
        } else
          gen.if(code_1.checkMissingProp(cxt, schema, missing)), code_1.reportMissingProp(cxt, missing), gen.else();
      }
      function loopAllRequired() {
        gen.forOf("prop", schemaCode, (prop) => {
          cxt.setParams({ missingProperty: prop }), gen.if(code_1.noPropertyInData(gen, data, prop, opts.ownProperties), () => cxt.error());
        });
      }
      function loopUntilMissing(missing, valid) {
        cxt.setParams({ missingProperty: missing }), gen.forOf(missing, schemaCode, () => {
          gen.assign(valid, code_1.propertyInData(gen, data, missing, opts.ownProperties)), gen.if(codegen_1.not(valid), () => {
            cxt.error(), gen.break();
          });
        }, codegen_1.nil);
      }
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/validation/limitItems.js
var require_limitItems = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var codegen_1 = require_codegen(), error = {
    message({ keyword, schemaCode }) {
      return codegen_1.str`must NOT have ${keyword === "maxItems" ? "more" : "fewer"} than ${schemaCode} items`;
    },
    params: ({ schemaCode }) => codegen_1._`{limit: ${schemaCode}}`
  }, def = {
    keyword: ["maxItems", "minItems"],
    type: "array",
    schemaType: "number",
    $data: !0,
    error,
    code(cxt) {
      let { keyword, data, schemaCode } = cxt, op = keyword === "maxItems" ? codegen_1.operators.GT : codegen_1.operators.LT;
      cxt.fail$data(codegen_1._`${data}.length ${op} ${schemaCode}`);
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/runtime/equal.js
var require_equal = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var equal = require_fast_deep_equal();
  equal.code = 'require("ajv/dist/runtime/equal").default';
  exports.default = equal;
});

// node_modules/ajv/dist/vocabularies/validation/uniqueItems.js
var require_uniqueItems = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var dataType_1 = require_dataType(), codegen_1 = require_codegen(), util_1 = require_util(), equal_1 = require_equal(), error = {
    message: ({ params: { i, j } }) => codegen_1.str`must NOT have duplicate items (items ## ${j} and ${i} are identical)`,
    params: ({ params: { i, j } }) => codegen_1._`{i: ${i}, j: ${j}}`
  }, def = {
    keyword: "uniqueItems",
    type: "array",
    schemaType: "boolean",
    $data: !0,
    error,
    code(cxt) {
      let { gen, data, $data, schema, parentSchema, schemaCode, it } = cxt;
      if (!$data && !schema)
        return;
      let valid = gen.let("valid"), itemTypes = parentSchema.items ? dataType_1.getSchemaTypes(parentSchema.items) : [];
      cxt.block$data(valid, validateUniqueItems, codegen_1._`${schemaCode} === false`), cxt.ok(valid);
      function validateUniqueItems() {
        let i = gen.let("i", codegen_1._`${data}.length`), j = gen.let("j");
        cxt.setParams({ i, j }), gen.assign(valid, !0), gen.if(codegen_1._`${i} > 1`, () => (canOptimize() ? loopN : loopN2)(i, j));
      }
      function canOptimize() {
        return itemTypes.length > 0 && !itemTypes.some((t) => t === "object" || t === "array");
      }
      function loopN(i, j) {
        let item = gen.name("item"), wrongType = dataType_1.checkDataTypes(itemTypes, item, it.opts.strictNumbers, dataType_1.DataType.Wrong), indices = gen.const("indices", codegen_1._`{}`);
        gen.for(codegen_1._`;${i}--;`, () => {
          if (gen.let(item, codegen_1._`${data}[${i}]`), gen.if(wrongType, codegen_1._`continue`), itemTypes.length > 1)
            gen.if(codegen_1._`typeof ${item} == "string"`, codegen_1._`${item} += "_"`);
          gen.if(codegen_1._`typeof ${indices}[${item}] == "number"`, () => {
            gen.assign(j, codegen_1._`${indices}[${item}]`), cxt.error(), gen.assign(valid, !1).break();
          }).code(codegen_1._`${indices}[${item}] = ${i}`);
        });
      }
      function loopN2(i, j) {
        let eql = util_1.useFunc(gen, equal_1.default), outer = gen.name("outer");
        gen.label(outer).for(codegen_1._`;${i}--;`, () => gen.for(codegen_1._`${j} = ${i}; ${j}--;`, () => gen.if(codegen_1._`${eql}(${data}[${i}], ${data}[${j}])`, () => {
          cxt.error(), gen.assign(valid, !1).break(outer);
        })));
      }
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/validation/const.js
var require_const = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var codegen_1 = require_codegen(), util_1 = require_util(), equal_1 = require_equal(), error = {
    message: "must be equal to constant",
    params: ({ schemaCode }) => codegen_1._`{allowedValue: ${schemaCode}}`
  }, def = {
    keyword: "const",
    $data: !0,
    error,
    code(cxt) {
      let { gen, data, $data, schemaCode, schema } = cxt;
      if ($data || schema && typeof schema == "object")
        cxt.fail$data(codegen_1._`!${util_1.useFunc(gen, equal_1.default)}(${data}, ${schemaCode})`);
      else
        cxt.fail(codegen_1._`${schema} !== ${data}`);
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/validation/enum.js
var require_enum = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var codegen_1 = require_codegen(), util_1 = require_util(), equal_1 = require_equal(), error = {
    message: "must be equal to one of the allowed values",
    params: ({ schemaCode }) => codegen_1._`{allowedValues: ${schemaCode}}`
  }, def = {
    keyword: "enum",
    schemaType: "array",
    $data: !0,
    error,
    code(cxt) {
      let { gen, data, $data, schema, schemaCode, it } = cxt;
      if (!$data && schema.length === 0)
        throw Error("enum must have non-empty array");
      let useLoop = schema.length >= it.opts.loopEnum, eql, getEql = () => eql !== null && eql !== void 0 ? eql : eql = util_1.useFunc(gen, equal_1.default), valid;
      if (useLoop || $data)
        valid = gen.let("valid"), cxt.block$data(valid, loopEnum);
      else {
        if (!Array.isArray(schema))
          throw Error("ajv implementation error");
        let vSchema = gen.const("vSchema", schemaCode);
        valid = codegen_1.or(...schema.map((_x, i) => equalCode(vSchema, i)));
      }
      cxt.pass(valid);
      function loopEnum() {
        gen.assign(valid, !1), gen.forOf("v", schemaCode, (v) => gen.if(codegen_1._`${getEql()}(${data}, ${v})`, () => gen.assign(valid, !0).break()));
      }
      function equalCode(vSchema, i) {
        let sch = schema[i];
        return typeof sch === "object" && sch !== null ? codegen_1._`${getEql()}(${data}, ${vSchema}[${i}])` : codegen_1._`${data} === ${sch}`;
      }
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/validation/index.js
var require_validation = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var limitNumber_1 = require_limitNumber(), multipleOf_1 = require_multipleOf(), limitLength_1 = require_limitLength(), pattern_1 = require_pattern(), limitProperties_1 = require_limitProperties(), required_1 = require_required(), limitItems_1 = require_limitItems(), uniqueItems_1 = require_uniqueItems(), const_1 = require_const(), enum_1 = require_enum(), validation = [
    limitNumber_1.default,
    multipleOf_1.default,
    limitLength_1.default,
    pattern_1.default,
    limitProperties_1.default,
    required_1.default,
    limitItems_1.default,
    uniqueItems_1.default,
    { keyword: "type", schemaType: ["string", "array"] },
    { keyword: "nullable", schemaType: "boolean" },
    const_1.default,
    enum_1.default
  ];
  exports.default = validation;
});

// node_modules/ajv/dist/vocabularies/applicator/additionalItems.js
var require_additionalItems = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.validateAdditionalItems = void 0;
  var codegen_1 = require_codegen(), util_1 = require_util(), error = {
    message: ({ params: { len } }) => codegen_1.str`must NOT have more than ${len} items`,
    params: ({ params: { len } }) => codegen_1._`{limit: ${len}}`
  }, def = {
    keyword: "additionalItems",
    type: "array",
    schemaType: ["boolean", "object"],
    before: "uniqueItems",
    error,
    code(cxt) {
      let { parentSchema, it } = cxt, { items } = parentSchema;
      if (!Array.isArray(items)) {
        util_1.checkStrictMode(it, '"additionalItems" is ignored when "items" is not an array of schemas');
        return;
      }
      validateAdditionalItems(cxt, items);
    }
  };
  function validateAdditionalItems(cxt, items) {
    let { gen, schema, data, keyword, it } = cxt;
    it.items = !0;
    let len = gen.const("len", codegen_1._`${data}.length`);
    if (schema === !1)
      cxt.setParams({ len: items.length }), cxt.pass(codegen_1._`${len} <= ${items.length}`);
    else if (typeof schema == "object" && !util_1.alwaysValidSchema(it, schema)) {
      let valid = gen.var("valid", codegen_1._`${len} <= ${items.length}`);
      gen.if(codegen_1.not(valid), () => validateItems(valid)), cxt.ok(valid);
    }
    function validateItems(valid) {
      gen.forRange("i", items.length, len, (i) => {
        if (cxt.subschema({ keyword, dataProp: i, dataPropType: util_1.Type.Num }, valid), !it.allErrors)
          gen.if(codegen_1.not(valid), () => gen.break());
      });
    }
  }
  exports.validateAdditionalItems = validateAdditionalItems;
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/applicator/items.js
var require_items = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.validateTuple = void 0;
  var codegen_1 = require_codegen(), util_1 = require_util(), code_1 = require_code2(), def = {
    keyword: "items",
    type: "array",
    schemaType: ["object", "array", "boolean"],
    before: "uniqueItems",
    code(cxt) {
      let { schema, it } = cxt;
      if (Array.isArray(schema))
        return validateTuple(cxt, "additionalItems", schema);
      if (it.items = !0, util_1.alwaysValidSchema(it, schema))
        return;
      cxt.ok(code_1.validateArray(cxt));
    }
  };
  function validateTuple(cxt, extraItems, schArr = cxt.schema) {
    let { gen, parentSchema, data, keyword, it } = cxt;
    if (checkStrictTuple(parentSchema), it.opts.unevaluated && schArr.length && it.items !== !0)
      it.items = util_1.mergeEvaluated.items(gen, schArr.length, it.items);
    let valid = gen.name("valid"), len = gen.const("len", codegen_1._`${data}.length`);
    schArr.forEach((sch, i) => {
      if (util_1.alwaysValidSchema(it, sch))
        return;
      gen.if(codegen_1._`${len} > ${i}`, () => cxt.subschema({
        keyword,
        schemaProp: i,
        dataProp: i
      }, valid)), cxt.ok(valid);
    });
    function checkStrictTuple(sch) {
      let { opts, errSchemaPath } = it, l = schArr.length, fullTuple = l === sch.minItems && (l === sch.maxItems || sch[extraItems] === !1);
      if (opts.strictTuples && !fullTuple) {
        let msg = `"${keyword}" is ${l}-tuple, but minItems or maxItems/${extraItems} are not specified or different at path "${errSchemaPath}"`;
        util_1.checkStrictMode(it, msg, opts.strictTuples);
      }
    }
  }
  exports.validateTuple = validateTuple;
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/applicator/prefixItems.js
var require_prefixItems = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var items_1 = require_items(), def = {
    keyword: "prefixItems",
    type: "array",
    schemaType: ["array"],
    before: "uniqueItems",
    code: (cxt) => items_1.validateTuple(cxt, "items")
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/applicator/items2020.js
var require_items2020 = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var codegen_1 = require_codegen(), util_1 = require_util(), code_1 = require_code2(), additionalItems_1 = require_additionalItems(), error = {
    message: ({ params: { len } }) => codegen_1.str`must NOT have more than ${len} items`,
    params: ({ params: { len } }) => codegen_1._`{limit: ${len}}`
  }, def = {
    keyword: "items",
    type: "array",
    schemaType: ["object", "boolean"],
    before: "uniqueItems",
    error,
    code(cxt) {
      let { schema, parentSchema, it } = cxt, { prefixItems } = parentSchema;
      if (it.items = !0, util_1.alwaysValidSchema(it, schema))
        return;
      if (prefixItems)
        additionalItems_1.validateAdditionalItems(cxt, prefixItems);
      else
        cxt.ok(code_1.validateArray(cxt));
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/applicator/contains.js
var require_contains = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var codegen_1 = require_codegen(), util_1 = require_util(), error = {
    message: ({ params: { min, max } }) => max === void 0 ? codegen_1.str`must contain at least ${min} valid item(s)` : codegen_1.str`must contain at least ${min} and no more than ${max} valid item(s)`,
    params: ({ params: { min, max } }) => max === void 0 ? codegen_1._`{minContains: ${min}}` : codegen_1._`{minContains: ${min}, maxContains: ${max}}`
  }, def = {
    keyword: "contains",
    type: "array",
    schemaType: ["object", "boolean"],
    before: "uniqueItems",
    trackErrors: !0,
    error,
    code(cxt) {
      let { gen, schema, parentSchema, data, it } = cxt, min, max, { minContains, maxContains } = parentSchema;
      if (it.opts.next)
        min = minContains === void 0 ? 1 : minContains, max = maxContains;
      else
        min = 1;
      let len = gen.const("len", codegen_1._`${data}.length`);
      if (cxt.setParams({ min, max }), max === void 0 && min === 0) {
        util_1.checkStrictMode(it, '"minContains" == 0 without "maxContains": "contains" keyword ignored');
        return;
      }
      if (max !== void 0 && min > max) {
        util_1.checkStrictMode(it, '"minContains" > "maxContains" is always invalid'), cxt.fail();
        return;
      }
      if (util_1.alwaysValidSchema(it, schema)) {
        let cond = codegen_1._`${len} >= ${min}`;
        if (max !== void 0)
          cond = codegen_1._`${cond} && ${len} <= ${max}`;
        cxt.pass(cond);
        return;
      }
      it.items = !0;
      let valid = gen.name("valid");
      if (max === void 0 && min === 1)
        validateItems(valid, () => gen.if(valid, () => gen.break()));
      else if (min === 0) {
        if (gen.let(valid, !0), max !== void 0)
          gen.if(codegen_1._`${data}.length > 0`, validateItemsWithCount);
      } else
        gen.let(valid, !1), validateItemsWithCount();
      cxt.result(valid, () => cxt.reset());
      function validateItemsWithCount() {
        let schValid = gen.name("_valid"), count = gen.let("count", 0);
        validateItems(schValid, () => gen.if(schValid, () => checkLimits(count)));
      }
      function validateItems(_valid, block) {
        gen.forRange("i", 0, len, (i) => {
          cxt.subschema({
            keyword: "contains",
            dataProp: i,
            dataPropType: util_1.Type.Num,
            compositeRule: !0
          }, _valid), block();
        });
      }
      function checkLimits(count) {
        if (gen.code(codegen_1._`${count}++`), max === void 0)
          gen.if(codegen_1._`${count} >= ${min}`, () => gen.assign(valid, !0).break());
        else if (gen.if(codegen_1._`${count} > ${max}`, () => gen.assign(valid, !1).break()), min === 1)
          gen.assign(valid, !0);
        else
          gen.if(codegen_1._`${count} >= ${min}`, () => gen.assign(valid, !0));
      }
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/applicator/dependencies.js
var require_dependencies = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.validateSchemaDeps = exports.validatePropertyDeps = exports.error = void 0;
  var codegen_1 = require_codegen(), util_1 = require_util(), code_1 = require_code2();
  exports.error = {
    message: ({ params: { property, depsCount, deps } }) => codegen_1.str`must have ${depsCount === 1 ? "property" : "properties"} ${deps} when property ${property} is present`,
    params: ({ params: { property, depsCount, deps, missingProperty } }) => codegen_1._`{property: ${property},
    missingProperty: ${missingProperty},
    depsCount: ${depsCount},
    deps: ${deps}}`
  };
  var def = {
    keyword: "dependencies",
    type: "object",
    schemaType: "object",
    error: exports.error,
    code(cxt) {
      let [propDeps, schDeps] = splitDependencies(cxt);
      validatePropertyDeps(cxt, propDeps), validateSchemaDeps(cxt, schDeps);
    }
  };
  function splitDependencies({ schema }) {
    let propertyDeps = {}, schemaDeps = {};
    for (let key in schema) {
      if (key === "__proto__")
        continue;
      let deps = Array.isArray(schema[key]) ? propertyDeps : schemaDeps;
      deps[key] = schema[key];
    }
    return [propertyDeps, schemaDeps];
  }
  function validatePropertyDeps(cxt, propertyDeps = cxt.schema) {
    let { gen, data, it } = cxt;
    if (Object.keys(propertyDeps).length === 0)
      return;
    let missing = gen.let("missing");
    for (let prop in propertyDeps) {
      let deps = propertyDeps[prop];
      if (deps.length === 0)
        continue;
      let hasProperty = code_1.propertyInData(gen, data, prop, it.opts.ownProperties);
      if (cxt.setParams({
        property: prop,
        depsCount: deps.length,
        deps: deps.join(", ")
      }), it.allErrors)
        gen.if(hasProperty, () => {
          for (let depProp of deps)
            code_1.checkReportMissingProp(cxt, depProp);
        });
      else
        gen.if(codegen_1._`${hasProperty} && (${code_1.checkMissingProp(cxt, deps, missing)})`), code_1.reportMissingProp(cxt, missing), gen.else();
    }
  }
  exports.validatePropertyDeps = validatePropertyDeps;
  function validateSchemaDeps(cxt, schemaDeps = cxt.schema) {
    let { gen, data, keyword, it } = cxt, valid = gen.name("valid");
    for (let prop in schemaDeps) {
      if (util_1.alwaysValidSchema(it, schemaDeps[prop]))
        continue;
      gen.if(code_1.propertyInData(gen, data, prop, it.opts.ownProperties), () => {
        let schCxt = cxt.subschema({ keyword, schemaProp: prop }, valid);
        cxt.mergeValidEvaluated(schCxt, valid);
      }, () => gen.var(valid, !0)), cxt.ok(valid);
    }
  }
  exports.validateSchemaDeps = validateSchemaDeps;
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/applicator/propertyNames.js
var require_propertyNames = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var codegen_1 = require_codegen(), util_1 = require_util(), error = {
    message: "property name must be valid",
    params: ({ params }) => codegen_1._`{propertyName: ${params.propertyName}}`
  }, def = {
    keyword: "propertyNames",
    type: "object",
    schemaType: ["object", "boolean"],
    error,
    code(cxt) {
      let { gen, schema, data, it } = cxt;
      if (util_1.alwaysValidSchema(it, schema))
        return;
      let valid = gen.name("valid");
      gen.forIn("key", data, (key) => {
        cxt.setParams({ propertyName: key }), cxt.subschema({
          keyword: "propertyNames",
          data: key,
          dataTypes: ["string"],
          propertyName: key,
          compositeRule: !0
        }, valid), gen.if(codegen_1.not(valid), () => {
          if (cxt.error(!0), !it.allErrors)
            gen.break();
        });
      }), cxt.ok(valid);
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/applicator/additionalProperties.js
var require_additionalProperties = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var code_1 = require_code2(), codegen_1 = require_codegen(), names_1 = require_names(), util_1 = require_util(), error = {
    message: "must NOT have additional properties",
    params: ({ params }) => codegen_1._`{additionalProperty: ${params.additionalProperty}}`
  }, def = {
    keyword: "additionalProperties",
    type: ["object"],
    schemaType: ["boolean", "object"],
    allowUndefined: !0,
    trackErrors: !0,
    error,
    code(cxt) {
      let { gen, schema, parentSchema, data, errsCount, it } = cxt;
      if (!errsCount)
        throw Error("ajv implementation error");
      let { allErrors, opts } = it;
      if (it.props = !0, opts.removeAdditional !== "all" && util_1.alwaysValidSchema(it, schema))
        return;
      let props = code_1.allSchemaProperties(parentSchema.properties), patProps = code_1.allSchemaProperties(parentSchema.patternProperties);
      checkAdditionalProperties(), cxt.ok(codegen_1._`${errsCount} === ${names_1.default.errors}`);
      function checkAdditionalProperties() {
        gen.forIn("key", data, (key) => {
          if (!props.length && !patProps.length)
            additionalPropertyCode(key);
          else
            gen.if(isAdditional(key), () => additionalPropertyCode(key));
        });
      }
      function isAdditional(key) {
        let definedProp;
        if (props.length > 8) {
          let propsSchema = util_1.schemaRefOrVal(it, parentSchema.properties, "properties");
          definedProp = code_1.isOwnProperty(gen, propsSchema, key);
        } else if (props.length)
          definedProp = codegen_1.or(...props.map((p) => codegen_1._`${key} === ${p}`));
        else
          definedProp = codegen_1.nil;
        if (patProps.length)
          definedProp = codegen_1.or(definedProp, ...patProps.map((p) => codegen_1._`${code_1.usePattern(cxt, p)}.test(${key})`));
        return codegen_1.not(definedProp);
      }
      function deleteAdditional(key) {
        gen.code(codegen_1._`delete ${data}[${key}]`);
      }
      function additionalPropertyCode(key) {
        if (opts.removeAdditional === "all" || opts.removeAdditional && schema === !1) {
          deleteAdditional(key);
          return;
        }
        if (schema === !1) {
          if (cxt.setParams({ additionalProperty: key }), cxt.error(), !allErrors)
            gen.break();
          return;
        }
        if (typeof schema == "object" && !util_1.alwaysValidSchema(it, schema)) {
          let valid = gen.name("valid");
          if (opts.removeAdditional === "failing")
            applyAdditionalSchema(key, valid, !1), gen.if(codegen_1.not(valid), () => {
              cxt.reset(), deleteAdditional(key);
            });
          else if (applyAdditionalSchema(key, valid), !allErrors)
            gen.if(codegen_1.not(valid), () => gen.break());
        }
      }
      function applyAdditionalSchema(key, valid, errors) {
        let subschema = {
          keyword: "additionalProperties",
          dataProp: key,
          dataPropType: util_1.Type.Str
        };
        if (errors === !1)
          Object.assign(subschema, {
            compositeRule: !0,
            createErrors: !1,
            allErrors: !1
          });
        cxt.subschema(subschema, valid);
      }
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/applicator/properties.js
var require_properties = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var validate_1 = require_validate(), code_1 = require_code2(), util_1 = require_util(), additionalProperties_1 = require_additionalProperties(), def = {
    keyword: "properties",
    type: "object",
    schemaType: "object",
    code(cxt) {
      let { gen, schema, parentSchema, data, it } = cxt;
      if (it.opts.removeAdditional === "all" && parentSchema.additionalProperties === void 0)
        additionalProperties_1.default.code(new validate_1.KeywordCxt(it, additionalProperties_1.default, "additionalProperties"));
      let allProps = code_1.allSchemaProperties(schema);
      for (let prop of allProps)
        it.definedProperties.add(prop);
      if (it.opts.unevaluated && allProps.length && it.props !== !0)
        it.props = util_1.mergeEvaluated.props(gen, util_1.toHash(allProps), it.props);
      let properties = allProps.filter((p) => !util_1.alwaysValidSchema(it, schema[p]));
      if (properties.length === 0)
        return;
      let valid = gen.name("valid");
      for (let prop of properties) {
        if (hasDefault(prop))
          applyPropertySchema(prop);
        else {
          if (gen.if(code_1.propertyInData(gen, data, prop, it.opts.ownProperties)), applyPropertySchema(prop), !it.allErrors)
            gen.else().var(valid, !0);
          gen.endIf();
        }
        cxt.it.definedProperties.add(prop), cxt.ok(valid);
      }
      function hasDefault(prop) {
        return it.opts.useDefaults && !it.compositeRule && schema[prop].default !== void 0;
      }
      function applyPropertySchema(prop) {
        cxt.subschema({
          keyword: "properties",
          schemaProp: prop,
          dataProp: prop
        }, valid);
      }
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/applicator/patternProperties.js
var require_patternProperties = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var code_1 = require_code2(), codegen_1 = require_codegen(), util_1 = require_util(), util_2 = require_util(), def = {
    keyword: "patternProperties",
    type: "object",
    schemaType: "object",
    code(cxt) {
      let { gen, schema, data, parentSchema, it } = cxt, { opts } = it, patterns = code_1.allSchemaProperties(schema), alwaysValidPatterns = patterns.filter((p) => util_1.alwaysValidSchema(it, schema[p]));
      if (patterns.length === 0 || alwaysValidPatterns.length === patterns.length && (!it.opts.unevaluated || it.props === !0))
        return;
      let checkProperties = opts.strictSchema && !opts.allowMatchingProperties && parentSchema.properties, valid = gen.name("valid");
      if (it.props !== !0 && !(it.props instanceof codegen_1.Name))
        it.props = util_2.evaluatedPropsToName(gen, it.props);
      let { props } = it;
      validatePatternProperties();
      function validatePatternProperties() {
        for (let pat of patterns) {
          if (checkProperties)
            checkMatchingProperties(pat);
          if (it.allErrors)
            validateProperties(pat);
          else
            gen.var(valid, !0), validateProperties(pat), gen.if(valid);
        }
      }
      function checkMatchingProperties(pat) {
        for (let prop in checkProperties)
          if (new RegExp(pat).test(prop))
            util_1.checkStrictMode(it, `property ${prop} matches pattern ${pat} (use allowMatchingProperties)`);
      }
      function validateProperties(pat) {
        gen.forIn("key", data, (key) => {
          gen.if(codegen_1._`${code_1.usePattern(cxt, pat)}.test(${key})`, () => {
            let alwaysValid = alwaysValidPatterns.includes(pat);
            if (!alwaysValid)
              cxt.subschema({
                keyword: "patternProperties",
                schemaProp: pat,
                dataProp: key,
                dataPropType: util_2.Type.Str
              }, valid);
            if (it.opts.unevaluated && props !== !0)
              gen.assign(codegen_1._`${props}[${key}]`, !0);
            else if (!alwaysValid && !it.allErrors)
              gen.if(codegen_1.not(valid), () => gen.break());
          });
        });
      }
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/applicator/not.js
var require_not = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var util_1 = require_util(), def = {
    keyword: "not",
    schemaType: ["object", "boolean"],
    trackErrors: !0,
    code(cxt) {
      let { gen, schema, it } = cxt;
      if (util_1.alwaysValidSchema(it, schema)) {
        cxt.fail();
        return;
      }
      let valid = gen.name("valid");
      cxt.subschema({
        keyword: "not",
        compositeRule: !0,
        createErrors: !1,
        allErrors: !1
      }, valid), cxt.failResult(valid, () => cxt.reset(), () => cxt.error());
    },
    error: { message: "must NOT be valid" }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/applicator/anyOf.js
var require_anyOf = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var code_1 = require_code2(), def = {
    keyword: "anyOf",
    schemaType: "array",
    trackErrors: !0,
    code: code_1.validateUnion,
    error: { message: "must match a schema in anyOf" }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/applicator/oneOf.js
var require_oneOf = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var codegen_1 = require_codegen(), util_1 = require_util(), error = {
    message: "must match exactly one schema in oneOf",
    params: ({ params }) => codegen_1._`{passingSchemas: ${params.passing}}`
  }, def = {
    keyword: "oneOf",
    schemaType: "array",
    trackErrors: !0,
    error,
    code(cxt) {
      let { gen, schema, parentSchema, it } = cxt;
      if (!Array.isArray(schema))
        throw Error("ajv implementation error");
      if (it.opts.discriminator && parentSchema.discriminator)
        return;
      let schArr = schema, valid = gen.let("valid", !1), passing = gen.let("passing", null), schValid = gen.name("_valid");
      cxt.setParams({ passing }), gen.block(validateOneOf), cxt.result(valid, () => cxt.reset(), () => cxt.error(!0));
      function validateOneOf() {
        schArr.forEach((sch, i) => {
          let schCxt;
          if (util_1.alwaysValidSchema(it, sch))
            gen.var(schValid, !0);
          else
            schCxt = cxt.subschema({
              keyword: "oneOf",
              schemaProp: i,
              compositeRule: !0
            }, schValid);
          if (i > 0)
            gen.if(codegen_1._`${schValid} && ${valid}`).assign(valid, !1).assign(passing, codegen_1._`[${passing}, ${i}]`).else();
          gen.if(schValid, () => {
            if (gen.assign(valid, !0), gen.assign(passing, i), schCxt)
              cxt.mergeEvaluated(schCxt, codegen_1.Name);
          });
        });
      }
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/applicator/allOf.js
var require_allOf = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var util_1 = require_util(), def = {
    keyword: "allOf",
    schemaType: "array",
    code(cxt) {
      let { gen, schema, it } = cxt;
      if (!Array.isArray(schema))
        throw Error("ajv implementation error");
      let valid = gen.name("valid");
      schema.forEach((sch, i) => {
        if (util_1.alwaysValidSchema(it, sch))
          return;
        let schCxt = cxt.subschema({ keyword: "allOf", schemaProp: i }, valid);
        cxt.ok(valid), cxt.mergeEvaluated(schCxt);
      });
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/applicator/if.js
var require_if = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var codegen_1 = require_codegen(), util_1 = require_util(), error = {
    message: ({ params }) => codegen_1.str`must match "${params.ifClause}" schema`,
    params: ({ params }) => codegen_1._`{failingKeyword: ${params.ifClause}}`
  }, def = {
    keyword: "if",
    schemaType: ["object", "boolean"],
    trackErrors: !0,
    error,
    code(cxt) {
      let { gen, parentSchema, it } = cxt;
      if (parentSchema.then === void 0 && parentSchema.else === void 0)
        util_1.checkStrictMode(it, '"if" without "then" and "else" is ignored');
      let hasThen = hasSchema(it, "then"), hasElse = hasSchema(it, "else");
      if (!hasThen && !hasElse)
        return;
      let valid = gen.let("valid", !0), schValid = gen.name("_valid");
      if (validateIf(), cxt.reset(), hasThen && hasElse) {
        let ifClause = gen.let("ifClause");
        cxt.setParams({ ifClause }), gen.if(schValid, validateClause("then", ifClause), validateClause("else", ifClause));
      } else if (hasThen)
        gen.if(schValid, validateClause("then"));
      else
        gen.if(codegen_1.not(schValid), validateClause("else"));
      cxt.pass(valid, () => cxt.error(!0));
      function validateIf() {
        let schCxt = cxt.subschema({
          keyword: "if",
          compositeRule: !0,
          createErrors: !1,
          allErrors: !1
        }, schValid);
        cxt.mergeEvaluated(schCxt);
      }
      function validateClause(keyword, ifClause) {
        return () => {
          let schCxt = cxt.subschema({ keyword }, schValid);
          if (gen.assign(valid, schValid), cxt.mergeValidEvaluated(schCxt, valid), ifClause)
            gen.assign(ifClause, codegen_1._`${keyword}`);
          else
            cxt.setParams({ ifClause: keyword });
        };
      }
    }
  };
  function hasSchema(it, keyword) {
    let schema = it.schema[keyword];
    return schema !== void 0 && !util_1.alwaysValidSchema(it, schema);
  }
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/applicator/thenElse.js
var require_thenElse = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var util_1 = require_util(), def = {
    keyword: ["then", "else"],
    schemaType: ["object", "boolean"],
    code({ keyword, parentSchema, it }) {
      if (parentSchema.if === void 0)
        util_1.checkStrictMode(it, `"${keyword}" without "if" is ignored`);
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/applicator/index.js
var require_applicator = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var additionalItems_1 = require_additionalItems(), prefixItems_1 = require_prefixItems(), items_1 = require_items(), items2020_1 = require_items2020(), contains_1 = require_contains(), dependencies_1 = require_dependencies(), propertyNames_1 = require_propertyNames(), additionalProperties_1 = require_additionalProperties(), properties_1 = require_properties(), patternProperties_1 = require_patternProperties(), not_1 = require_not(), anyOf_1 = require_anyOf(), oneOf_1 = require_oneOf(), allOf_1 = require_allOf(), if_1 = require_if(), thenElse_1 = require_thenElse();
  function getApplicator(draft2020 = !1) {
    let applicator = [
      not_1.default,
      anyOf_1.default,
      oneOf_1.default,
      allOf_1.default,
      if_1.default,
      thenElse_1.default,
      propertyNames_1.default,
      additionalProperties_1.default,
      dependencies_1.default,
      properties_1.default,
      patternProperties_1.default
    ];
    if (draft2020)
      applicator.push(prefixItems_1.default, items2020_1.default);
    else
      applicator.push(additionalItems_1.default, items_1.default);
    return applicator.push(contains_1.default), applicator;
  }
  exports.default = getApplicator;
});

// node_modules/ajv/dist/vocabularies/format/format.js
var require_format = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var codegen_1 = require_codegen(), error = {
    message: ({ schemaCode }) => codegen_1.str`must match format "${schemaCode}"`,
    params: ({ schemaCode }) => codegen_1._`{format: ${schemaCode}}`
  }, def = {
    keyword: "format",
    type: ["number", "string"],
    schemaType: "string",
    $data: !0,
    error,
    code(cxt, ruleType) {
      let { gen, data, $data, schema, schemaCode, it } = cxt, { opts, errSchemaPath, schemaEnv, self } = it;
      if (!opts.validateFormats)
        return;
      if ($data)
        validate$DataFormat();
      else
        validateFormat();
      function validate$DataFormat() {
        let fmts = gen.scopeValue("formats", {
          ref: self.formats,
          code: opts.code.formats
        }), fDef = gen.const("fDef", codegen_1._`${fmts}[${schemaCode}]`), fType = gen.let("fType"), format = gen.let("format");
        gen.if(codegen_1._`typeof ${fDef} == "object" && !(${fDef} instanceof RegExp)`, () => gen.assign(fType, codegen_1._`${fDef}.type || "string"`).assign(format, codegen_1._`${fDef}.validate`), () => gen.assign(fType, codegen_1._`"string"`).assign(format, fDef)), cxt.fail$data(codegen_1.or(unknownFmt(), invalidFmt()));
        function unknownFmt() {
          if (opts.strictSchema === !1)
            return codegen_1.nil;
          return codegen_1._`${schemaCode} && !${format}`;
        }
        function invalidFmt() {
          let callFormat = schemaEnv.$async ? codegen_1._`(${fDef}.async ? await ${format}(${data}) : ${format}(${data}))` : codegen_1._`${format}(${data})`, validData = codegen_1._`(typeof ${format} == "function" ? ${callFormat} : ${format}.test(${data}))`;
          return codegen_1._`${format} && ${format} !== true && ${fType} === ${ruleType} && !${validData}`;
        }
      }
      function validateFormat() {
        let formatDef = self.formats[schema];
        if (!formatDef) {
          unknownFormat();
          return;
        }
        if (formatDef === !0)
          return;
        let [fmtType, format, fmtRef] = getFormat(formatDef);
        if (fmtType === ruleType)
          cxt.pass(validCondition());
        function unknownFormat() {
          if (opts.strictSchema === !1) {
            self.logger.warn(unknownMsg());
            return;
          }
          throw Error(unknownMsg());
          function unknownMsg() {
            return `unknown format "${schema}" ignored in schema at path "${errSchemaPath}"`;
          }
        }
        function getFormat(fmtDef) {
          let code = fmtDef instanceof RegExp ? codegen_1.regexpCode(fmtDef) : opts.code.formats ? codegen_1._`${opts.code.formats}${codegen_1.getProperty(schema)}` : void 0, fmt = gen.scopeValue("formats", { key: schema, ref: fmtDef, code });
          if (typeof fmtDef == "object" && !(fmtDef instanceof RegExp))
            return [fmtDef.type || "string", fmtDef.validate, codegen_1._`${fmt}.validate`];
          return ["string", fmtDef, fmt];
        }
        function validCondition() {
          if (typeof formatDef == "object" && !(formatDef instanceof RegExp) && formatDef.async) {
            if (!schemaEnv.$async)
              throw Error("async format in sync schema");
            return codegen_1._`await ${fmtRef}(${data})`;
          }
          return typeof format == "function" ? codegen_1._`${fmtRef}(${data})` : codegen_1._`${fmtRef}.test(${data})`;
        }
      }
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/vocabularies/format/index.js
var require_format2 = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var format_1 = require_format(), format = [format_1.default];
  exports.default = format;
});

// node_modules/ajv/dist/vocabularies/metadata.js
var require_metadata = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.contentVocabulary = exports.metadataVocabulary = void 0;
  exports.metadataVocabulary = [
    "title",
    "description",
    "default",
    "deprecated",
    "readOnly",
    "writeOnly",
    "examples"
  ];
  exports.contentVocabulary = [
    "contentMediaType",
    "contentEncoding",
    "contentSchema"
  ];
});

// node_modules/ajv/dist/vocabularies/draft7.js
var require_draft7 = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var core_1 = require_core2(), validation_1 = require_validation(), applicator_1 = require_applicator(), format_1 = require_format2(), metadata_1 = require_metadata(), draft7Vocabularies = [
    core_1.default,
    validation_1.default,
    (0, applicator_1.default)(),
    format_1.default,
    metadata_1.metadataVocabulary,
    metadata_1.contentVocabulary
  ];
  exports.default = draft7Vocabularies;
});

// node_modules/ajv/dist/vocabularies/discriminator/types.js
var require_types = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.DiscrError = void 0;
  var DiscrError;
  (function(DiscrError) {
    DiscrError.Tag = "tag", DiscrError.Mapping = "mapping";
  })(DiscrError || (exports.DiscrError = DiscrError = {}));
});

// node_modules/ajv/dist/vocabularies/discriminator/index.js
var require_discriminator = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var codegen_1 = require_codegen(), types_1 = require_types(), compile_1 = require_compile(), ref_error_1 = require_ref_error(), util_1 = require_util(), error = {
    message: ({ params: { discrError, tagName } }) => discrError === types_1.DiscrError.Tag ? `tag "${tagName}" must be string` : `value of tag "${tagName}" must be in oneOf`,
    params: ({ params: { discrError, tag, tagName } }) => codegen_1._`{error: ${discrError}, tag: ${tagName}, tagValue: ${tag}}`
  }, def = {
    keyword: "discriminator",
    type: "object",
    schemaType: "object",
    error,
    code(cxt) {
      let { gen, data, schema, parentSchema, it } = cxt, { oneOf } = parentSchema;
      if (!it.opts.discriminator)
        throw Error("discriminator: requires discriminator option");
      let tagName = schema.propertyName;
      if (typeof tagName != "string")
        throw Error("discriminator: requires propertyName");
      if (schema.mapping)
        throw Error("discriminator: mapping is not supported");
      if (!oneOf)
        throw Error("discriminator: requires oneOf keyword");
      let valid = gen.let("valid", !1), tag = gen.const("tag", codegen_1._`${data}${codegen_1.getProperty(tagName)}`);
      gen.if(codegen_1._`typeof ${tag} == "string"`, () => validateMapping(), () => cxt.error(!1, { discrError: types_1.DiscrError.Tag, tag, tagName })), cxt.ok(valid);
      function validateMapping() {
        let mapping = getMapping();
        gen.if(!1);
        for (let tagValue in mapping)
          gen.elseIf(codegen_1._`${tag} === ${tagValue}`), gen.assign(valid, applyTagSchema(mapping[tagValue]));
        gen.else(), cxt.error(!1, { discrError: types_1.DiscrError.Mapping, tag, tagName }), gen.endIf();
      }
      function applyTagSchema(schemaProp) {
        let _valid = gen.name("valid"), schCxt = cxt.subschema({ keyword: "oneOf", schemaProp }, _valid);
        return cxt.mergeEvaluated(schCxt, codegen_1.Name), _valid;
      }
      function getMapping() {
        var _a;
        let oneOfMapping = {}, topRequired = hasRequired(parentSchema), tagRequired = !0;
        for (let i = 0;i < oneOf.length; i++) {
          let sch = oneOf[i];
          if ((sch === null || sch === void 0 ? void 0 : sch.$ref) && !util_1.schemaHasRulesButRef(sch, it.self.RULES)) {
            let ref = sch.$ref;
            if (sch = compile_1.resolveRef.call(it.self, it.schemaEnv.root, it.baseId, ref), sch instanceof compile_1.SchemaEnv)
              sch = sch.schema;
            if (sch === void 0)
              throw new ref_error_1.default(it.opts.uriResolver, it.baseId, ref);
          }
          let propSch = (_a = sch === null || sch === void 0 ? void 0 : sch.properties) === null || _a === void 0 ? void 0 : _a[tagName];
          if (typeof propSch != "object")
            throw Error(`discriminator: oneOf subschemas (or referenced schemas) must have "properties/${tagName}"`);
          tagRequired = tagRequired && (topRequired || hasRequired(sch)), addMappings(propSch, i);
        }
        if (!tagRequired)
          throw Error(`discriminator: "${tagName}" must be required`);
        return oneOfMapping;
        function hasRequired({ required }) {
          return Array.isArray(required) && required.includes(tagName);
        }
        function addMappings(sch, i) {
          if (sch.const)
            addMapping(sch.const, i);
          else if (sch.enum)
            for (let tagValue of sch.enum)
              addMapping(tagValue, i);
          else
            throw Error(`discriminator: "properties/${tagName}" must have "const" or "enum"`);
        }
        function addMapping(tagValue, i) {
          if (typeof tagValue != "string" || tagValue in oneOfMapping)
            throw Error(`discriminator: "${tagName}" values must be unique strings`);
          oneOfMapping[tagValue] = i;
        }
      }
    }
  };
  exports.default = def;
});

// node_modules/ajv/dist/refs/json-schema-draft-07.json
var require_json_schema_draft_07 = __commonJS(function(exports, module) {
  module.exports = {
    $schema: "http://json-schema.org/draft-07/schema#",
    $id: "http://json-schema.org/draft-07/schema#",
    title: "Core schema meta-schema",
    definitions: {
      schemaArray: {
        type: "array",
        minItems: 1,
        items: { $ref: "#" }
      },
      nonNegativeInteger: {
        type: "integer",
        minimum: 0
      },
      nonNegativeIntegerDefault0: {
        allOf: [{ $ref: "#/definitions/nonNegativeInteger" }, { default: 0 }]
      },
      simpleTypes: {
        enum: ["array", "boolean", "integer", "null", "number", "object", "string"]
      },
      stringArray: {
        type: "array",
        items: { type: "string" },
        uniqueItems: !0,
        default: []
      }
    },
    type: ["object", "boolean"],
    properties: {
      $id: {
        type: "string",
        format: "uri-reference"
      },
      $schema: {
        type: "string",
        format: "uri"
      },
      $ref: {
        type: "string",
        format: "uri-reference"
      },
      $comment: {
        type: "string"
      },
      title: {
        type: "string"
      },
      description: {
        type: "string"
      },
      default: !0,
      readOnly: {
        type: "boolean",
        default: !1
      },
      examples: {
        type: "array",
        items: !0
      },
      multipleOf: {
        type: "number",
        exclusiveMinimum: 0
      },
      maximum: {
        type: "number"
      },
      exclusiveMaximum: {
        type: "number"
      },
      minimum: {
        type: "number"
      },
      exclusiveMinimum: {
        type: "number"
      },
      maxLength: { $ref: "#/definitions/nonNegativeInteger" },
      minLength: { $ref: "#/definitions/nonNegativeIntegerDefault0" },
      pattern: {
        type: "string",
        format: "regex"
      },
      additionalItems: { $ref: "#" },
      items: {
        anyOf: [{ $ref: "#" }, { $ref: "#/definitions/schemaArray" }],
        default: !0
      },
      maxItems: { $ref: "#/definitions/nonNegativeInteger" },
      minItems: { $ref: "#/definitions/nonNegativeIntegerDefault0" },
      uniqueItems: {
        type: "boolean",
        default: !1
      },
      contains: { $ref: "#" },
      maxProperties: { $ref: "#/definitions/nonNegativeInteger" },
      minProperties: { $ref: "#/definitions/nonNegativeIntegerDefault0" },
      required: { $ref: "#/definitions/stringArray" },
      additionalProperties: { $ref: "#" },
      definitions: {
        type: "object",
        additionalProperties: { $ref: "#" },
        default: {}
      },
      properties: {
        type: "object",
        additionalProperties: { $ref: "#" },
        default: {}
      },
      patternProperties: {
        type: "object",
        additionalProperties: { $ref: "#" },
        propertyNames: { format: "regex" },
        default: {}
      },
      dependencies: {
        type: "object",
        additionalProperties: {
          anyOf: [{ $ref: "#" }, { $ref: "#/definitions/stringArray" }]
        }
      },
      propertyNames: { $ref: "#" },
      const: !0,
      enum: {
        type: "array",
        items: !0,
        minItems: 1,
        uniqueItems: !0
      },
      type: {
        anyOf: [
          { $ref: "#/definitions/simpleTypes" },
          {
            type: "array",
            items: { $ref: "#/definitions/simpleTypes" },
            minItems: 1,
            uniqueItems: !0
          }
        ]
      },
      format: { type: "string" },
      contentMediaType: { type: "string" },
      contentEncoding: { type: "string" },
      if: { $ref: "#" },
      then: { $ref: "#" },
      else: { $ref: "#" },
      allOf: { $ref: "#/definitions/schemaArray" },
      anyOf: { $ref: "#/definitions/schemaArray" },
      oneOf: { $ref: "#/definitions/schemaArray" },
      not: { $ref: "#" }
    },
    default: !0
  };
});

// node_modules/ajv/dist/ajv.js
var require_ajv = __commonJS(function(exports, module) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.MissingRefError = exports.ValidationError = exports.CodeGen = exports.Name = exports.nil = exports.stringify = exports.str = exports._ = exports.KeywordCxt = exports.Ajv = void 0;
  var core_1 = require_core(), draft7_1 = require_draft7(), discriminator_1 = require_discriminator(), draft7MetaSchema = require_json_schema_draft_07(), META_SUPPORT_DATA = ["/properties"], META_SCHEMA_ID = "http://json-schema.org/draft-07/schema";

  class Ajv extends core_1.default {
    _addVocabularies() {
      if (super._addVocabularies(), draft7_1.default.forEach((v) => this.addVocabulary(v)), this.opts.discriminator)
        this.addKeyword(discriminator_1.default);
    }
    _addDefaultMetaSchema() {
      if (super._addDefaultMetaSchema(), !this.opts.meta)
        return;
      let metaSchema = this.opts.$data ? this.$dataMetaSchema(draft7MetaSchema, META_SUPPORT_DATA) : draft7MetaSchema;
      this.addMetaSchema(metaSchema, META_SCHEMA_ID, !1), this.refs["http://json-schema.org/schema"] = META_SCHEMA_ID;
    }
    defaultMeta() {
      return this.opts.defaultMeta = super.defaultMeta() || (this.getSchema(META_SCHEMA_ID) ? META_SCHEMA_ID : void 0);
    }
  }
  exports.Ajv = Ajv;
  module.exports = exports = Ajv;
  module.exports.Ajv = Ajv;
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.default = Ajv;
  var validate_1 = require_validate();
  Object.defineProperty(exports, "KeywordCxt", { enumerable: !0, get: function() {
    return validate_1.KeywordCxt;
  } });
  var codegen_1 = require_codegen();
  Object.defineProperty(exports, "_", { enumerable: !0, get: function() {
    return codegen_1._;
  } });
  Object.defineProperty(exports, "str", { enumerable: !0, get: function() {
    return codegen_1.str;
  } });
  Object.defineProperty(exports, "stringify", { enumerable: !0, get: function() {
    return codegen_1.stringify;
  } });
  Object.defineProperty(exports, "nil", { enumerable: !0, get: function() {
    return codegen_1.nil;
  } });
  Object.defineProperty(exports, "Name", { enumerable: !0, get: function() {
    return codegen_1.Name;
  } });
  Object.defineProperty(exports, "CodeGen", { enumerable: !0, get: function() {
    return codegen_1.CodeGen;
  } });
  var validation_error_1 = require_validation_error();
  Object.defineProperty(exports, "ValidationError", { enumerable: !0, get: function() {
    return validation_error_1.default;
  } });
  var ref_error_1 = require_ref_error();
  Object.defineProperty(exports, "MissingRefError", { enumerable: !0, get: function() {
    return ref_error_1.default;
  } });
});

// node_modules/ajv-formats/dist/formats.js
var require_formats = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.formatNames = exports.fastFormats = exports.fullFormats = void 0;
  function fmtDef(validate, compare) {
    return { validate, compare };
  }
  exports.fullFormats = {
    date: fmtDef(date, compareDate),
    time: fmtDef(getTime(!0), compareTime),
    "date-time": fmtDef(getDateTime(!0), compareDateTime),
    "iso-time": fmtDef(getTime(), compareIsoTime),
    "iso-date-time": fmtDef(getDateTime(), compareIsoDateTime),
    duration: /^P(?!$)((\d+Y)?(\d+M)?(\d+D)?(T(?=\d)(\d+H)?(\d+M)?(\d+S)?)?|(\d+W)?)$/,
    uri,
    "uri-reference": /^(?:[a-z][a-z0-9+\-.]*:)?(?:\/?\/(?:(?:[a-z0-9\-._~!$&'()*+,;=:]|%[0-9a-f]{2})*@)?(?:\[(?:(?:(?:(?:[0-9a-f]{1,4}:){6}|::(?:[0-9a-f]{1,4}:){5}|(?:[0-9a-f]{1,4})?::(?:[0-9a-f]{1,4}:){4}|(?:(?:[0-9a-f]{1,4}:){0,1}[0-9a-f]{1,4})?::(?:[0-9a-f]{1,4}:){3}|(?:(?:[0-9a-f]{1,4}:){0,2}[0-9a-f]{1,4})?::(?:[0-9a-f]{1,4}:){2}|(?:(?:[0-9a-f]{1,4}:){0,3}[0-9a-f]{1,4})?::[0-9a-f]{1,4}:|(?:(?:[0-9a-f]{1,4}:){0,4}[0-9a-f]{1,4})?::)(?:[0-9a-f]{1,4}:[0-9a-f]{1,4}|(?:(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d\d?))|(?:(?:[0-9a-f]{1,4}:){0,5}[0-9a-f]{1,4})?::[0-9a-f]{1,4}|(?:(?:[0-9a-f]{1,4}:){0,6}[0-9a-f]{1,4})?::)|[Vv][0-9a-f]+\.[a-z0-9\-._~!$&'()*+,;=:]+)\]|(?:(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d\d?)|(?:[a-z0-9\-._~!$&'"()*+,;=]|%[0-9a-f]{2})*)(?::\d*)?(?:\/(?:[a-z0-9\-._~!$&'"()*+,;=:@]|%[0-9a-f]{2})*)*|\/(?:(?:[a-z0-9\-._~!$&'"()*+,;=:@]|%[0-9a-f]{2})+(?:\/(?:[a-z0-9\-._~!$&'"()*+,;=:@]|%[0-9a-f]{2})*)*)?|(?:[a-z0-9\-._~!$&'"()*+,;=:@]|%[0-9a-f]{2})+(?:\/(?:[a-z0-9\-._~!$&'"()*+,;=:@]|%[0-9a-f]{2})*)*)?(?:\?(?:[a-z0-9\-._~!$&'"()*+,;=:@/?]|%[0-9a-f]{2})*)?(?:#(?:[a-z0-9\-._~!$&'"()*+,;=:@/?]|%[0-9a-f]{2})*)?$/i,
    "uri-template": /^(?:(?:[^\x00-\x20"'<>%\\^`{|}]|%[0-9a-f]{2})|\{[+#./;?&=,!@|]?(?:[a-z0-9_]|%[0-9a-f]{2})+(?::[1-9][0-9]{0,3}|\*)?(?:,(?:[a-z0-9_]|%[0-9a-f]{2})+(?::[1-9][0-9]{0,3}|\*)?)*\})*$/i,
    url: /^(?:https?|ftp):\/\/(?:\S+(?::\S*)?@)?(?:(?!(?:10|127)(?:\.\d{1,3}){3})(?!(?:169\.254|192\.168)(?:\.\d{1,3}){2})(?!172\.(?:1[6-9]|2\d|3[0-1])(?:\.\d{1,3}){2})(?:[1-9]\d?|1\d\d|2[01]\d|22[0-3])(?:\.(?:1?\d{1,2}|2[0-4]\d|25[0-5])){2}(?:\.(?:[1-9]\d?|1\d\d|2[0-4]\d|25[0-4]))|(?:(?:[a-z0-9\u{00a1}-\u{ffff}]+-)*[a-z0-9\u{00a1}-\u{ffff}]+)(?:\.(?:[a-z0-9\u{00a1}-\u{ffff}]+-)*[a-z0-9\u{00a1}-\u{ffff}]+)*(?:\.(?:[a-z\u{00a1}-\u{ffff}]{2,})))(?::\d{2,5})?(?:\/[^\s]*)?$/iu,
    email: /^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/i,
    hostname: /^(?=.{1,253}\.?$)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[-0-9a-z]{0,61}[0-9a-z])?)*\.?$/i,
    ipv4: /^(?:(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$/,
    ipv6: /^((([0-9a-f]{1,4}:){7}([0-9a-f]{1,4}|:))|(([0-9a-f]{1,4}:){6}(:[0-9a-f]{1,4}|((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})|:))|(([0-9a-f]{1,4}:){5}(((:[0-9a-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})|:))|(([0-9a-f]{1,4}:){4}(((:[0-9a-f]{1,4}){1,3})|((:[0-9a-f]{1,4})?:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9a-f]{1,4}:){3}(((:[0-9a-f]{1,4}){1,4})|((:[0-9a-f]{1,4}){0,2}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9a-f]{1,4}:){2}(((:[0-9a-f]{1,4}){1,5})|((:[0-9a-f]{1,4}){0,3}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9a-f]{1,4}:){1}(((:[0-9a-f]{1,4}){1,6})|((:[0-9a-f]{1,4}){0,4}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(:(((:[0-9a-f]{1,4}){1,7})|((:[0-9a-f]{1,4}){0,5}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:)))$/i,
    regex,
    uuid: /^(?:urn:uuid:)?[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i,
    "json-pointer": /^(?:\/(?:[^~/]|~0|~1)*)*$/,
    "json-pointer-uri-fragment": /^#(?:\/(?:[a-z0-9_\-.!$&'()*+,;:=@]|%[0-9a-f]{2}|~0|~1)*)*$/i,
    "relative-json-pointer": /^(?:0|[1-9][0-9]*)(?:#|(?:\/(?:[^~/]|~0|~1)*)*)$/,
    byte,
    int32: { type: "number", validate: validateInt32 },
    int64: { type: "number", validate: validateInt64 },
    float: { type: "number", validate: validateNumber },
    double: { type: "number", validate: validateNumber },
    password: !0,
    binary: !0
  };
  exports.fastFormats = {
    ...exports.fullFormats,
    date: fmtDef(/^\d\d\d\d-[0-1]\d-[0-3]\d$/, compareDate),
    time: fmtDef(/^(?:[0-2]\d:[0-5]\d:[0-5]\d|23:59:60)(?:\.\d+)?(?:z|[+-]\d\d(?::?\d\d)?)$/i, compareTime),
    "date-time": fmtDef(/^\d\d\d\d-[0-1]\d-[0-3]\dt(?:[0-2]\d:[0-5]\d:[0-5]\d|23:59:60)(?:\.\d+)?(?:z|[+-]\d\d(?::?\d\d)?)$/i, compareDateTime),
    "iso-time": fmtDef(/^(?:[0-2]\d:[0-5]\d:[0-5]\d|23:59:60)(?:\.\d+)?(?:z|[+-]\d\d(?::?\d\d)?)?$/i, compareIsoTime),
    "iso-date-time": fmtDef(/^\d\d\d\d-[0-1]\d-[0-3]\d[t\s](?:[0-2]\d:[0-5]\d:[0-5]\d|23:59:60)(?:\.\d+)?(?:z|[+-]\d\d(?::?\d\d)?)?$/i, compareIsoDateTime),
    uri: /^(?:[a-z][a-z0-9+\-.]*:)(?:\/?\/)?[^\s]*$/i,
    "uri-reference": /^(?:(?:[a-z][a-z0-9+\-.]*:)?\/?\/)?(?:[^\\\s#][^\s#]*)?(?:#[^\\\s]*)?$/i,
    email: /^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*$/i
  };
  exports.formatNames = Object.keys(exports.fullFormats);
  function isLeapYear(year) {
    return year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  }
  var DATE = /^(\d\d\d\d)-(\d\d)-(\d\d)$/, DAYS = [0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  function date(str) {
    let matches = DATE.exec(str);
    if (!matches)
      return !1;
    let year = +matches[1], month = +matches[2], day = +matches[3];
    return month >= 1 && month <= 12 && day >= 1 && day <= (month === 2 && isLeapYear(year) ? 29 : DAYS[month]);
  }
  function compareDate(d1, d2) {
    if (!(d1 && d2))
      return;
    if (d1 > d2)
      return 1;
    if (d1 < d2)
      return -1;
    return 0;
  }
  var TIME = /^(\d\d):(\d\d):(\d\d(?:\.\d+)?)(z|([+-])(\d\d)(?::?(\d\d))?)?$/i;
  function getTime(strictTimeZone) {
    return function(str) {
      let matches = TIME.exec(str);
      if (!matches)
        return !1;
      let hr = +matches[1], min = +matches[2], sec = +matches[3], tz = matches[4], tzSign = matches[5] === "-" ? -1 : 1, tzH = +(matches[6] || 0), tzM = +(matches[7] || 0);
      if (tzH > 23 || tzM > 59 || strictTimeZone && !tz)
        return !1;
      if (hr <= 23 && min <= 59 && sec < 60)
        return !0;
      let utcMin = min - tzM * tzSign, utcHr = hr - tzH * tzSign - (utcMin < 0 ? 1 : 0);
      return (utcHr === 23 || utcHr === -1) && (utcMin === 59 || utcMin === -1) && sec < 61;
    };
  }
  function compareTime(s1, s2) {
    if (!(s1 && s2))
      return;
    let t1 = (/* @__PURE__ */ new Date("2020-01-01T" + s1)).valueOf(), t2 = (/* @__PURE__ */ new Date("2020-01-01T" + s2)).valueOf();
    if (!(t1 && t2))
      return;
    return t1 - t2;
  }
  function compareIsoTime(t1, t2) {
    if (!(t1 && t2))
      return;
    let a1 = TIME.exec(t1), a2 = TIME.exec(t2);
    if (!(a1 && a2))
      return;
    if (t1 = a1[1] + a1[2] + a1[3], t2 = a2[1] + a2[2] + a2[3], t1 > t2)
      return 1;
    if (t1 < t2)
      return -1;
    return 0;
  }
  var DATE_TIME_SEPARATOR = /t|\s/i;
  function getDateTime(strictTimeZone) {
    let time = getTime(strictTimeZone);
    return function(str) {
      let dateTime = str.split(DATE_TIME_SEPARATOR);
      return dateTime.length === 2 && date(dateTime[0]) && time(dateTime[1]);
    };
  }
  function compareDateTime(dt1, dt2) {
    if (!(dt1 && dt2))
      return;
    let d1 = new Date(dt1).valueOf(), d2 = new Date(dt2).valueOf();
    if (!(d1 && d2))
      return;
    return d1 - d2;
  }
  function compareIsoDateTime(dt1, dt2) {
    if (!(dt1 && dt2))
      return;
    let [d1, t1] = dt1.split(DATE_TIME_SEPARATOR), [d2, t2] = dt2.split(DATE_TIME_SEPARATOR), res = compareDate(d1, d2);
    if (res === void 0)
      return;
    return res || compareTime(t1, t2);
  }
  var NOT_URI_FRAGMENT = /\/|:/, URI = /^(?:[a-z][a-z0-9+\-.]*:)(?:\/?\/(?:(?:[a-z0-9\-._~!$&'()*+,;=:]|%[0-9a-f]{2})*@)?(?:\[(?:(?:(?:(?:[0-9a-f]{1,4}:){6}|::(?:[0-9a-f]{1,4}:){5}|(?:[0-9a-f]{1,4})?::(?:[0-9a-f]{1,4}:){4}|(?:(?:[0-9a-f]{1,4}:){0,1}[0-9a-f]{1,4})?::(?:[0-9a-f]{1,4}:){3}|(?:(?:[0-9a-f]{1,4}:){0,2}[0-9a-f]{1,4})?::(?:[0-9a-f]{1,4}:){2}|(?:(?:[0-9a-f]{1,4}:){0,3}[0-9a-f]{1,4})?::[0-9a-f]{1,4}:|(?:(?:[0-9a-f]{1,4}:){0,4}[0-9a-f]{1,4})?::)(?:[0-9a-f]{1,4}:[0-9a-f]{1,4}|(?:(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d\d?))|(?:(?:[0-9a-f]{1,4}:){0,5}[0-9a-f]{1,4})?::[0-9a-f]{1,4}|(?:(?:[0-9a-f]{1,4}:){0,6}[0-9a-f]{1,4})?::)|[Vv][0-9a-f]+\.[a-z0-9\-._~!$&'()*+,;=:]+)\]|(?:(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d\d?)|(?:[a-z0-9\-._~!$&'()*+,;=]|%[0-9a-f]{2})*)(?::\d*)?(?:\/(?:[a-z0-9\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})*)*|\/(?:(?:[a-z0-9\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})+(?:\/(?:[a-z0-9\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})*)*)?|(?:[a-z0-9\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})+(?:\/(?:[a-z0-9\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})*)*)(?:\?(?:[a-z0-9\-._~!$&'()*+,;=:@/?]|%[0-9a-f]{2})*)?(?:#(?:[a-z0-9\-._~!$&'()*+,;=:@/?]|%[0-9a-f]{2})*)?$/i;
  function uri(str) {
    return NOT_URI_FRAGMENT.test(str) && URI.test(str);
  }
  var BYTE = /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/gm;
  function byte(str) {
    return BYTE.lastIndex = 0, BYTE.test(str);
  }
  var MIN_INT32 = -2147483648, MAX_INT32 = 2147483647;
  function validateInt32(value) {
    return Number.isInteger(value) && value <= MAX_INT32 && value >= MIN_INT32;
  }
  function validateInt64(value) {
    return Number.isInteger(value);
  }
  function validateNumber() {
    return !0;
  }
  var Z_ANCHOR = /[^\\]\\Z/;
  function regex(str) {
    if (Z_ANCHOR.test(str))
      return !1;
    try {
      return new RegExp(str), !0;
    } catch (e) {
      return !1;
    }
  }
});

// node_modules/ajv-formats/dist/limit.js
var require_limit = __commonJS(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.formatLimitDefinition = void 0;
  var ajv_1 = require_ajv(), codegen_1 = require_codegen(), ops = codegen_1.operators, KWDs = {
    formatMaximum: { okStr: "<=", ok: ops.LTE, fail: ops.GT },
    formatMinimum: { okStr: ">=", ok: ops.GTE, fail: ops.LT },
    formatExclusiveMaximum: { okStr: "<", ok: ops.LT, fail: ops.GTE },
    formatExclusiveMinimum: { okStr: ">", ok: ops.GT, fail: ops.LTE }
  }, error = {
    message: ({ keyword, schemaCode }) => codegen_1.str`should be ${KWDs[keyword].okStr} ${schemaCode}`,
    params: ({ keyword, schemaCode }) => codegen_1._`{comparison: ${KWDs[keyword].okStr}, limit: ${schemaCode}}`
  };
  exports.formatLimitDefinition = {
    keyword: Object.keys(KWDs),
    type: "string",
    schemaType: "string",
    $data: !0,
    error,
    code(cxt) {
      let { gen, data, schemaCode, keyword, it } = cxt, { opts, self } = it;
      if (!opts.validateFormats)
        return;
      let fCxt = new ajv_1.KeywordCxt(it, self.RULES.all.format.definition, "format");
      if (fCxt.$data)
        validate$DataFormat();
      else
        validateFormat();
      function validate$DataFormat() {
        let fmts = gen.scopeValue("formats", {
          ref: self.formats,
          code: opts.code.formats
        }), fmt = gen.const("fmt", codegen_1._`${fmts}[${fCxt.schemaCode}]`);
        cxt.fail$data(codegen_1.or(codegen_1._`typeof ${fmt} != "object"`, codegen_1._`${fmt} instanceof RegExp`, codegen_1._`typeof ${fmt}.compare != "function"`, compareCode(fmt)));
      }
      function validateFormat() {
        let format = fCxt.schema, fmtDef = self.formats[format];
        if (!fmtDef || fmtDef === !0)
          return;
        if (typeof fmtDef != "object" || fmtDef instanceof RegExp || typeof fmtDef.compare != "function")
          throw Error(`"${keyword}": format "${format}" does not define "compare" function`);
        let fmt = gen.scopeValue("formats", {
          key: format,
          ref: fmtDef,
          code: opts.code.formats ? codegen_1._`${opts.code.formats}${codegen_1.getProperty(format)}` : void 0
        });
        cxt.fail$data(compareCode(fmt));
      }
      function compareCode(fmt) {
        return codegen_1._`${fmt}.compare(${data}, ${schemaCode}) ${KWDs[keyword].fail} 0`;
      }
    },
    dependencies: ["format"]
  };
  var formatLimitPlugin = (ajv) => (ajv.addKeyword(exports.formatLimitDefinition), ajv);
  exports.default = formatLimitPlugin;
});

// node_modules/ajv-formats/dist/index.js
var require_dist = __commonJS(function(exports, module) {
  Object.defineProperty(exports, "__esModule", { value: !0 });
  var formats_1 = require_formats(), limit_1 = require_limit(), codegen_1 = require_codegen(), fullName = new codegen_1.Name("fullFormats"), fastName = new codegen_1.Name("fastFormats"), formatsPlugin = (ajv, opts = { keywords: !0 }) => {
    if (Array.isArray(opts))
      return addFormats(ajv, opts, formats_1.fullFormats, fullName), ajv;
    let [formats, exportName] = opts.mode === "fast" ? [formats_1.fastFormats, fastName] : [formats_1.fullFormats, fullName], list = opts.formats || formats_1.formatNames;
    if (addFormats(ajv, list, formats, exportName), opts.keywords)
      (0, limit_1.default)(ajv);
    return ajv;
  };
  formatsPlugin.get = (name, mode = "full") => {
    let f = (mode === "fast" ? formats_1.fastFormats : formats_1.fullFormats)[name];
    if (!f)
      throw Error(`Unknown format "${name}"`);
    return f;
  };
  function addFormats(ajv, list, fs, exportName) {
    var _a, _b;
    (_a = (_b = ajv.opts.code).formats) !== null && _a !== void 0 || (_b.formats = codegen_1._`require("ajv-formats/dist/formats").${exportName}`);
    for (let f of list)
      ajv.addFormat(f, fs[f]);
  }
  module.exports = exports = formatsPlugin;
  Object.defineProperty(exports, "__esModule", { value: !0 });
  exports.default = formatsPlugin;
});

// ij-mcp-proxy.ts
import path6 from "path";
import { cwd as cwd2, env as env2 } from "process";
import { fileURLToPath as fileURLToPath2 } from "url";

// node_modules/zod/v4/core/util.js
function getEnumValues(entries) {
  let numericValues = Object.values(entries).filter((v) => typeof v === "number");
  return Object.entries(entries).filter(([k, _]) => numericValues.indexOf(+k) === -1).map(([_, v]) => v);
}
function joinValues(array, separator = "|") {
  return array.map((val) => stringifyPrimitive(val)).join(separator);
}
function jsonStringifyReplacer(_, value) {
  if (typeof value === "bigint")
    return value.toString();
  return value;
}
function cached(getter) {
  return {
    get value() {
      {
        let value = getter();
        return Object.defineProperty(this, "value", { value }), value;
      }
      throw Error("cached value already set");
    }
  };
}
function nullish(input) {
  return input === null || input === void 0;
}
function cleanRegex(source) {
  let start = source.startsWith("^") ? 1 : 0, end = source.endsWith("$") ? source.length - 1 : source.length;
  return source.slice(start, end);
}
function floatSafeRemainder(val, step) {
  let ratio = val / step, roundedRatio = Math.round(ratio), tolerance = 4 * Number.EPSILON * Math.max(Math.abs(ratio), 1);
  if (Math.abs(ratio - roundedRatio) < tolerance)
    return 0;
  return ratio - roundedRatio;
}
function assignProp(target, prop, value) {
  Object.defineProperty(target, prop, {
    value,
    writable: !0,
    enumerable: !0,
    configurable: !0
  });
}
function mergeDefs(...defs) {
  let mergedDescriptors = {};
  for (let def of defs) {
    let descriptors = Object.getOwnPropertyDescriptors(def);
    Object.assign(mergedDescriptors, descriptors);
  }
  return Object.defineProperties({}, mergedDescriptors);
}
function esc(str) {
  return JSON.stringify(str);
}
function slugify(input) {
  return input.toLowerCase().trim().replace(/[^\w\s-]/g, "").replace(/[\s_-]+/g, "-").replace(/^-+|-+$/g, "");
}
var captureStackTrace = "captureStackTrace" in Error ? Error.captureStackTrace : (..._args) => {};
function isObject(data) {
  return typeof data === "object" && data !== null && !Array.isArray(data);
}
var allowsEval = /* @__PURE__ */ cached(() => {
  if (globalConfig.jitless)
    return !1;
  if (typeof navigator < "u" && navigator?.userAgent?.includes("Cloudflare"))
    return !1;
  try {
    return new Function(""), !0;
  } catch (_) {
    return !1;
  }
});
function isPlainObject(o) {
  if (isObject(o) === !1)
    return !1;
  let ctor = o.constructor;
  if (ctor === void 0)
    return !0;
  if (typeof ctor !== "function")
    return !0;
  let prot = ctor.prototype;
  if (isObject(prot) === !1)
    return !1;
  if (Object.prototype.hasOwnProperty.call(prot, "isPrototypeOf") === !1)
    return !1;
  return !0;
}
function shallowClone(o) {
  if (isPlainObject(o))
    return { ...o };
  if (Array.isArray(o))
    return [...o];
  if (o instanceof Map)
    return new Map(o);
  if (o instanceof Set)
    return new Set(o);
  return o;
}
var propertyKeyTypes = /* @__PURE__ */ new Set(["string", "number", "symbol"]);
function escapeRegex(str) {
  return str.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
function clone(inst, def, params) {
  let cl = new inst._zod.constr(def ?? inst._zod.def);
  if (!def || params?.parent)
    cl._zod.parent = inst;
  return cl;
}
function normalizeParams(_params) {
  let params = _params;
  if (!params)
    return {};
  if (typeof params === "string")
    return { error: () => params };
  if (params?.message !== void 0) {
    if (params?.error !== void 0)
      throw Error("Cannot specify both `message` and `error` params");
    params.error = params.message;
  }
  if (delete params.message, typeof params.error === "string")
    return { ...params, error: () => params.error };
  return params;
}
function stringifyPrimitive(value) {
  if (typeof value === "bigint")
    return value.toString() + "n";
  if (typeof value === "string")
    return `"${value}"`;
  return `${value}`;
}
function optionalKeys(shape) {
  return Object.keys(shape).filter((k) => shape[k]._zod.optin !== void 0 && shape[k]._zod.optout === "optional");
}
var NUMBER_FORMAT_RANGES = /* @__PURE__ */ (() => ({
  safeint: [Number.MIN_SAFE_INTEGER, Number.MAX_SAFE_INTEGER],
  int32: [-2147483648, 2147483647],
  uint32: [0, 4294967295],
  float32: [-340282346638528860000000000000000000000, 340282346638528860000000000000000000000],
  float64: [-Number.MAX_VALUE, Number.MAX_VALUE]
}))();
function pick(schema, mask) {
  let currDef = schema._zod.def, checks = currDef.checks;
  if (checks && checks.length > 0)
    throw Error(".pick() cannot be used on object schemas containing refinements");
  let def = mergeDefs(schema._zod.def, {
    get shape() {
      let newShape = {};
      for (let key of Reflect.ownKeys(mask)) {
        if (!Object.prototype.hasOwnProperty.call(currDef.shape, key))
          throw Error(`Unrecognized key: "${String(key)}"`);
        if (!mask[key])
          continue;
        assignProp(newShape, key, currDef.shape[key]);
      }
      return assignProp(this, "shape", newShape), newShape;
    },
    checks: []
  });
  return clone(schema, def);
}
function omit(schema, mask) {
  let currDef = schema._zod.def, checks = currDef.checks;
  if (checks && checks.length > 0)
    throw Error(".omit() cannot be used on object schemas containing refinements");
  let def = mergeDefs(schema._zod.def, {
    get shape() {
      let newShape = { ...schema._zod.def.shape };
      for (let key of Reflect.ownKeys(mask)) {
        if (!Object.prototype.hasOwnProperty.call(currDef.shape, key))
          throw Error(`Unrecognized key: "${String(key)}"`);
        if (!mask[key])
          continue;
        delete newShape[key];
      }
      return assignProp(this, "shape", newShape), newShape;
    },
    checks: []
  });
  return clone(schema, def);
}
function extend(schema, shape) {
  if (!isPlainObject(shape))
    throw Error("Invalid input to extend: expected a plain object");
  let checks = schema._zod.def.checks;
  if (checks && checks.length > 0) {
    let existingShape = schema._zod.def.shape;
    for (let key of Reflect.ownKeys(shape))
      if (Object.getOwnPropertyDescriptor(existingShape, key) !== void 0)
        throw Error("Cannot overwrite keys on object schemas containing refinements. Use `.safeExtend()` instead.");
  }
  let def = mergeDefs(schema._zod.def, {
    get shape() {
      let _shape = { ...schema._zod.def.shape, ...shape };
      return assignProp(this, "shape", _shape), _shape;
    }
  });
  return clone(schema, def);
}
function safeExtend(schema, shape) {
  if (!isPlainObject(shape))
    throw Error("Invalid input to safeExtend: expected a plain object");
  let def = mergeDefs(schema._zod.def, {
    get shape() {
      let _shape = { ...schema._zod.def.shape, ...shape };
      return assignProp(this, "shape", _shape), _shape;
    }
  });
  return clone(schema, def);
}
function merge(a, b) {
  if (!b?._zod?.def)
    throw Error("Invalid input to merge: expected an object schema. To merge a plain shape, use `.extend()`.");
  if (a._zod.def.checks?.length)
    throw Error(".merge() cannot be used on object schemas containing refinements. Use .safeExtend() instead.");
  let def = mergeDefs(a._zod.def, {
    get shape() {
      let _shape = { ...a._zod.def.shape, ...b._zod.def.shape };
      return assignProp(this, "shape", _shape), _shape;
    },
    get catchall() {
      return b._zod.def.catchall;
    },
    checks: b._zod.def.checks ?? []
  });
  return clone(a, def);
}
function partial(Class, schema, mask, name = "partial") {
  let checks = schema._zod.def.checks;
  if (checks && checks.length > 0)
    throw Error(`.${name}() cannot be used on object schemas containing refinements`);
  let def = mergeDefs(schema._zod.def, {
    get shape() {
      let oldShape = schema._zod.def.shape, shape = { ...oldShape };
      if (mask)
        for (let key of Reflect.ownKeys(mask)) {
          if (!Object.prototype.hasOwnProperty.call(oldShape, key))
            throw Error(`Unrecognized key: "${String(key)}"`);
          if (!mask[key])
            continue;
          shape[key] = Class ? new Class({
            type: "optional",
            innerType: oldShape[key]
          }) : oldShape[key];
        }
      else
        for (let key of Reflect.ownKeys(oldShape))
          shape[key] = Class ? new Class({
            type: "optional",
            innerType: oldShape[key]
          }) : oldShape[key];
      return assignProp(this, "shape", shape), shape;
    },
    checks: []
  });
  return clone(schema, def);
}
function required(Class, schema, mask) {
  let def = mergeDefs(schema._zod.def, {
    get shape() {
      let oldShape = schema._zod.def.shape, shape = { ...oldShape };
      if (mask)
        for (let key of Reflect.ownKeys(mask)) {
          if (!Object.prototype.hasOwnProperty.call(shape, key))
            throw Error(`Unrecognized key: "${String(key)}"`);
          if (!mask[key])
            continue;
          shape[key] = new Class({
            type: "nonoptional",
            innerType: oldShape[key]
          });
        }
      else
        for (let key of Reflect.ownKeys(oldShape))
          shape[key] = new Class({
            type: "nonoptional",
            innerType: oldShape[key]
          });
      return assignProp(this, "shape", shape), shape;
    }
  });
  return clone(schema, def);
}
function aborted(x, startIndex = 0) {
  if (x.aborted === !0)
    return !0;
  for (let i = startIndex;i < x.issues.length; i++)
    if (x.issues[i]?.continue !== !0)
      return !0;
  return !1;
}
function explicitlyAborted(x, startIndex = 0) {
  if (x.aborted === !0)
    return !0;
  for (let i = startIndex;i < x.issues.length; i++)
    if (x.issues[i]?.continue === !1)
      return !0;
  return !1;
}
function prefixIssues(path, issues) {
  return issues.map((iss) => {
    var _a;
    return (_a = iss).path ?? (_a.path = []), iss.path.unshift(path), iss;
  });
}
function unwrapMessage(message) {
  return typeof message === "string" ? message : message?.message;
}
function attachSchema(issues, start, inst) {
  var _a;
  for (let i = start;i < issues.length; i++)
    (_a = issues[i]).schema ?? (_a.schema = inst);
}
function finalizeIssue(iss, ctx, config) {
  var _a;
  let traits = iss.inst?._zod?.traits;
  if (traits?.has("$ZodType"))
    if (traits.has("$ZodCheck"))
      (_a = iss).schema ?? (_a.schema = iss.inst);
    else
      iss.schema = iss.inst;
  let schemaError = iss.schema !== iss.inst ? iss.schema?._zod.def?.error : void 0, message = iss.message ? iss.message : unwrapMessage(iss.inst?._zod.def?.error?.(iss)) ?? unwrapMessage(schemaError?.(iss)) ?? unwrapMessage(ctx?.error?.(iss)) ?? unwrapMessage(config.customError?.(iss)) ?? unwrapMessage(config.localeError?.(iss)) ?? "Invalid input", { inst: _inst, schema: _schema, continue: _continue, input: _input, ...rest } = iss;
  if (rest.path ?? (rest.path = []), rest.message = message, ctx?.reportInput)
    rest.input = _input;
  return rest;
}
var highSurrogate = /[\uD800-\uDBFF]/;
function codePointLength(str) {
  let units = str.length;
  if (!highSurrogate.test(str))
    return units;
  let count = units;
  for (let i = 0;i < units - 1; i++)
    if ((str.charCodeAt(i) & 64512) === 55296 && (str.charCodeAt(i + 1) & 64512) === 56320)
      count--, i++;
  return count;
}
function getLengthableOrigin(input) {
  if (Array.isArray(input))
    return "array";
  if (typeof input === "string")
    return "string";
  return "unknown";
}
function parsedType(data) {
  let t = typeof data;
  switch (t) {
    case "number":
      return Number.isNaN(data) ? "nan" : "number";
    case "object": {
      if (data === null)
        return "null";
      if (Array.isArray(data))
        return "array";
      let obj = data;
      if (obj && Object.getPrototypeOf(obj) !== Object.prototype && "constructor" in obj && obj.constructor)
        return obj.constructor.name;
    }
  }
  return t;
}
function issue(...args) {
  let [iss, input, inst] = args;
  if (typeof iss === "string")
    return {
      message: iss,
      code: "custom",
      input,
      inst
    };
  return { ...iss };
}
function members(proto, table) {
  for (let key in table) {
    let desc = Object.getOwnPropertyDescriptor(table, key);
    if (desc.get)
      Object.defineProperty(proto, key, { ...desc, enumerable: !1 });
    else
      defineBound(proto, key, desc.value);
  }
}
function own(inst, key, value, enumerable = !0) {
  return Object.defineProperty(inst, key, { configurable: !0, writable: !0, enumerable, value }), value;
}
function hide(inst, key, value) {
  return own(inst, key, value, !1);
}
function defineBound(proto, key, fn) {
  Object.defineProperty(proto, key, {
    configurable: !0,
    get() {
      return this == null ? fn : own(this, key, fn.bind(this));
    },
    set(value) {
      own(this, key, value);
    }
  });
}
function claim(inst, sentinel) {
  let proto = Object.getPrototypeOf(inst);
  return sentinel in proto ? void 0 : proto;
}
var installing, broke = !1, breaker = {
  configurable: !0,
  get() {
    broke = !0;
    return;
  }
};
function defineLazyInternal(inst, key, compute) {
  let proto = Object.getPrototypeOf(inst._zod);
  if (key in proto && installing !== inst._zod) {
    installing = void 0;
    return;
  }
  installing = inst._zod, Object.defineProperty(proto, key, {
    configurable: !0,
    get() {
      Object.defineProperty(this, key, breaker);
      let outer = broke;
      broke = !1;
      try {
        let value = compute(this);
        if (broke)
          delete this[key];
        else
          Object.defineProperty(this, key, { configurable: !0, writable: !0, value });
        return broke = broke || outer, value;
      } catch (err) {
        throw delete this[key], broke = broke || outer, err;
      }
    },
    set(value) {
      Object.defineProperty(this, key, { configurable: !0, writable: !0, value });
    }
  });
}
function installLazyProp(inst, key, make, enumerable) {
  let proto = claim(inst, key);
  if (!proto)
    return;
  Object.defineProperty(proto, key, {
    configurable: !0,
    get() {
      let desc = { configurable: !0, writable: !0, enumerable, value: void 0 };
      return Object.defineProperty(this, key, desc), desc.value = make(this), Object.defineProperty(this, key, desc), desc.value;
    },
    set(value) {
      Object.defineProperty(this, key, { configurable: !0, writable: !0, enumerable, value });
    }
  });
}
var CONSTANT_CATCH = "~constantCatch";
function constantCatch(value) {
  let fn = () => value;
  return fn[CONSTANT_CATCH] = !0, fn;
}

// node_modules/zod/v4/core/core.js
var _a, NEVER = /* @__PURE__ */ Object.freeze({
  status: "aborted"
}), _zodDesc = { value: void 0, enumerable: !1 }, _E = "captureStackTrace" in Error ? Error : null;
function newError(Definition) {
  let E = _E;
  if (E) {
    let saved = E.stackTraceLimit;
    if (typeof saved === "number") {
      try {
        E.stackTraceLimit = 0;
      } catch {
        return _E = null, new Definition;
      }
      try {
        return new Definition;
      } finally {
        E.stackTraceLimit = saved;
      }
    }
  }
  return new Definition;
}
function $constructor(name, initializer, proto, params) {
  let zodProto = {};
  function Internals(def) {
    this.def = def, this.constr = _, this.traits = /* @__PURE__ */ new Set;
  }
  Internals.prototype = zodProto;
  let protoMembers = proto, initialized = protoMembers && /* @__PURE__ */ new WeakSet;
  function init(inst, def) {
    if (!inst._zod) {
      _zodDesc.value = new Internals(def);
      try {
        Object.defineProperty(inst, "_zod", _zodDesc);
      } finally {
        _zodDesc.value = void 0;
      }
    }
    if (inst._zod.traits.has(name))
      return;
    if (inst._zod.traits.add(name), initializer(inst, def), initialized) {
      let own = Object.getPrototypeOf(inst), ctorProto = inst._zod.constr.prototype, up = own;
      while (up && up !== ctorProto)
        up = Object.getPrototypeOf(up);
      let target = up ?? own;
      if (!initialized.has(target))
        initialized.add(target), members(target, protoMembers);
    }
    let proto = _.prototype;
    for (let k in proto) {
      if (!Object.prototype.hasOwnProperty.call(proto, k))
        continue;
      if (!(k in inst))
        inst[k] = proto[k].bind(inst);
    }
  }
  let Parent = params?.Parent ?? Object;

  class Definition extends Parent {
  }
  Object.defineProperty(Definition, "name", { value: name });
  function _(def) {
    let inst = params?.Parent ? newError(Definition) : this;
    init(inst, def);
    let deferred = inst._zod.deferred;
    if (deferred) {
      for (let fn of deferred)
        fn();
      inst._zod.deferred = void 0;
    }
    let pp = globalThis.__zod_globalConfig?.postProcessor;
    if (pp)
      pp(inst);
    return inst;
  }
  return Object.defineProperty(_, "init", { value: init }), Object.defineProperty(_, Symbol.hasInstance, {
    value: (inst) => {
      if (params?.Parent && inst instanceof params.Parent)
        return !0;
      return inst?._zod?.traits?.has(name);
    }
  }), Object.defineProperty(_, "name", { value: name }), _;
}
class $ZodAsyncError extends Error {
  constructor() {
    super("Encountered Promise during synchronous parse. Use .parseAsync() instead.");
  }
}

class $ZodEncodeError extends Error {
  constructor(name) {
    super(`Encountered unidirectional transform during encode: ${name}`);
    this.name = "ZodEncodeError";
  }
}
(_a = globalThis).__zod_globalConfig ?? (_a.__zod_globalConfig = {});
var globalConfig = globalThis.__zod_globalConfig;
function config(newConfig) {
  if (newConfig)
    Object.assign(globalConfig, newConfig);
  return globalConfig;
}
// node_modules/zod/v4/core/errors.js
function _getMessage() {
  let internals = this._zod;
  return internals.message ?? (internals.message = JSON.stringify(internals.def, jsonStringifyReplacer, 2)), internals.message;
}
function _setMessage(value) {
  this._zod.message = value;
}
var _messageDesc = {
  get: _getMessage,
  set: _setMessage,
  enumerable: !0,
  configurable: !0
}, _zodDesc2 = { value: void 0, enumerable: !1 }, _issuesDesc = { value: void 0, enumerable: !1 }, _installedToString = /* @__PURE__ */ new WeakSet([Object.prototype, Error.prototype]), initializer = (inst, def) => {
  inst.name = "$ZodError", _zodDesc2.value = inst._zod, Object.defineProperty(inst, "_zod", _zodDesc2), _issuesDesc.value = def, Object.defineProperty(inst, "issues", _issuesDesc), _zodDesc2.value = void 0, _issuesDesc.value = void 0, Object.defineProperty(inst, "message", _messageDesc);
  let proto = Object.getPrototypeOf(inst);
  if (!_installedToString.has(proto))
    _installedToString.add(proto), Object.defineProperty(proto, "toString", {
      configurable: !0,
      enumerable: !1,
      get() {
        let value = () => this.message;
        return Object.defineProperty(this, "toString", { value, configurable: !0, writable: !0 }), value;
      },
      set(value) {
        Object.defineProperty(this, "toString", { value, configurable: !0, writable: !0 });
      }
    });
}, $ZodError = $constructor("$ZodError", initializer), $ZodRealError = $constructor("$ZodError", initializer, void 0, {
  Parent: Error
});
function node(obj, key, make) {
  if (!Object.prototype.hasOwnProperty.call(obj, key))
    if (key === "__proto__")
      Object.defineProperty(obj, key, { value: make(), writable: !0, enumerable: !0, configurable: !0 });
    else
      obj[key] = make();
  return obj[key];
}
function flattenError(error, mapper = (issue) => issue.message) {
  let fieldErrors = {}, formErrors = [];
  for (let sub of error.issues)
    if (sub.path.length > 0)
      node(fieldErrors, sub.path[0], () => []).push(mapper(sub));
    else
      formErrors.push(mapper(sub));
  return { formErrors, fieldErrors };
}
function formatError(error, mapper = (issue) => issue.message) {
  let fieldErrors = { _errors: [] }, processError = (error, path = []) => {
    for (let issue of error.issues)
      if (issue.code === "invalid_union" && issue.errors.length)
        issue.errors.map((issues) => processError({ issues }, [...path, ...issue.path]));
      else if (issue.code === "invalid_key")
        processError({ issues: issue.issues }, [...path, ...issue.path]);
      else if (issue.code === "invalid_element")
        processError({ issues: issue.issues }, [...path, ...issue.path]);
      else {
        let fullpath = [...path, ...issue.path];
        if (fullpath.length === 0)
          fieldErrors._errors.push(mapper(issue));
        else {
          let curr = fieldErrors, i = 0;
          while (i < fullpath.length) {
            let el = fullpath[i], terminal = i === fullpath.length - 1;
            if (el === "_errors") {
              if (terminal)
                curr._errors.push(mapper(issue));
              i++;
              continue;
            }
            if (!Object.prototype.hasOwnProperty.call(curr, el))
              Object.defineProperty(curr, el, {
                value: { _errors: [] },
                enumerable: !0,
                writable: !0,
                configurable: !0
              });
            let node = curr[el];
            if (terminal)
              node._errors.push(mapper(issue));
            curr = node, i++;
          }
        }
      }
  };
  return processError(error), fieldErrors;
}

// node_modules/zod/v4/core/parse.js
function finalizeParams(callee, params) {
  return { callee: params?.callee ?? callee, Err: params?.Err };
}
var _parse = (_Err) => {
  let fn = (schema, value, _ctx, _params) => {
    let ctx = _ctx ? { ..._ctx, async: !1 } : { async: !1 }, result = schema._zod.run({ value, issues: [] }, ctx);
    if (result instanceof Promise)
      throw new $ZodAsyncError;
    if (result.issues.length) {
      let e = new (_params?.Err ?? _Err)(result.issues.map((iss) => finalizeIssue(iss, ctx, config())));
      throw captureStackTrace(e, _params?.callee ?? fn), e;
    }
    return result.value;
  };
  return fn;
};
var _parseAsync = (_Err) => {
  let fn = async (schema, value, _ctx, params) => {
    let ctx = _ctx ? { ..._ctx, async: !0 } : { async: !0 }, result = schema._zod.run({ value, issues: [] }, ctx);
    if (result instanceof Promise)
      result = await result;
    if (result.issues.length) {
      let e = new (params?.Err ?? _Err)(result.issues.map((iss) => finalizeIssue(iss, ctx, config())));
      throw captureStackTrace(e, params?.callee ?? fn), e;
    }
    return result.value;
  };
  return fn;
};
var _safeParse = (_Err) => (schema, value, _ctx) => {
  let ctx = _ctx ? { ..._ctx, async: !1 } : { async: !1 }, result = schema._zod.run({ value, issues: [] }, ctx);
  if (result instanceof Promise)
    throw new $ZodAsyncError;
  return result.issues.length ? {
    success: !1,
    error: new (_Err ?? $ZodError)(result.issues.map((iss) => finalizeIssue(iss, ctx, config())))
  } : { success: !0, data: result.value };
}, safeParse = /* @__PURE__ */ _safeParse($ZodRealError), _safeParseAsync = (_Err) => async (schema, value, _ctx) => {
  let ctx = _ctx ? { ..._ctx, async: !0 } : { async: !0 }, result = schema._zod.run({ value, issues: [] }, ctx);
  if (result instanceof Promise)
    result = await result;
  return result.issues.length ? {
    success: !1,
    error: new _Err(result.issues.map((iss) => finalizeIssue(iss, ctx, config())))
  } : { success: !0, data: result.value };
}, safeParseAsync = /* @__PURE__ */ _safeParseAsync($ZodRealError);
var _encode = (_Err) => {
  let parse = _parse(_Err), fn = (schema, value, _ctx, _params) => {
    let ctx = _ctx ? { ..._ctx, direction: "backward" } : { direction: "backward" };
    return parse(schema, value, ctx, finalizeParams(fn, _params));
  };
  return fn;
};
var _decode = (_Err) => {
  let parse = _parse(_Err), fn = (schema, value, _ctx, _params) => parse(schema, value, _ctx, finalizeParams(fn, _params));
  return fn;
};
var _encodeAsync = (_Err) => {
  let parseAsync = _parseAsync(_Err), fn = async (schema, value, _ctx, _params) => {
    let ctx = _ctx ? { ..._ctx, direction: "backward" } : { direction: "backward" };
    return await parseAsync(schema, value, ctx, finalizeParams(fn, _params));
  };
  return fn;
};
var _decodeAsync = (_Err) => {
  let parseAsync = _parseAsync(_Err), fn = async (schema, value, _ctx, _params) => await parseAsync(schema, value, _ctx, finalizeParams(fn, _params));
  return fn;
};
var _safeEncode = (_Err) => (schema, value, _ctx) => {
  let ctx = _ctx ? { ..._ctx, direction: "backward" } : { direction: "backward" };
  return _safeParse(_Err)(schema, value, ctx);
};
var _safeDecode = (_Err) => (schema, value, _ctx) => _safeParse(_Err)(schema, value, _ctx);
var _safeEncodeAsync = (_Err) => async (schema, value, _ctx) => {
  let ctx = _ctx ? { ..._ctx, direction: "backward" } : { direction: "backward" };
  return _safeParseAsync(_Err)(schema, value, ctx);
};
var _safeDecodeAsync = (_Err) => async (schema, value, _ctx) => _safeParseAsync(_Err)(schema, value, _ctx);
// node_modules/zod/v4/core/regexes.js
var cuid = /^[cC][0-9a-z]{6,}$/, cuid2 = /^[0-9a-z]+$/, ulid = /^[0-7][0-9A-HJKMNP-TV-Za-hjkmnp-tv-z]{25}$/, xid = /^[0-9a-vA-V]{20}$/, ksuid = /^[A-Za-z0-9]{27}$/, nanoid = /^[a-zA-Z0-9_-]{21}$/;
function nanoidOfLength(length) {
  return new RegExp(`^[a-zA-Z0-9_-]{${length}}$`);
}
var duration = /^P(?:(\d+W)|(?!.*W)(?=\d|T\d)(\d+Y)?(\d+M)?(\d+D)?(T(?=\d)(\d+H)?(\d+M)?(\d+([.,]\d+)?S)?)?)$/;
var guid = /^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$/, uuid = (version) => {
  if (!version)
    return /^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}|00000000-0000-0000-0000-000000000000|ffffffff-ffff-ffff-ffff-ffffffffffff)$/;
  return new RegExp(`^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-${version}[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})$`);
};
var email = /^(?!\.)(?!.*\.\.)([A-Za-z0-9_'+\-\.]*)[A-Za-z0-9_+-]@([A-Za-z0-9][A-Za-z0-9\-]*\.)+[A-Za-z]{2,}$/;
var _emoji = "^[\\p{Extended_Pictographic}\\p{Emoji_Component}]+$";
function emoji() {
  return new RegExp(_emoji, "u");
}
var ipv4 = /^(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\.){3}(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])$/, ipv6 = /^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:))$/;
var cidrv4 = /^((25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\.){3}(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\/([0-9]|[1-2][0-9]|3[0-2])$/, cidrv6 = /^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:))\/(12[0-8]|1[01][0-9]|[1-9]?[0-9])$/, base64 = /^$|^(?:[0-9a-zA-Z+/]{4})*(?:(?:[0-9a-zA-Z+/]{2}==)|(?:[0-9a-zA-Z+/]{3}=))?$/, base64url = /^[A-Za-z0-9_-]*$/;
var httpProtocol = /^https?$/, e164 = /^\+[1-9]\d{6,14}$/;
var dateSource = "(?:(?:\\d\\d[2468][048]|\\d\\d[13579][26]|\\d\\d0[48]|[02468][048]00|[13579][26]00)-02-29|\\d{4}-(?:(?:0[13578]|1[02])-(?:0[1-9]|[12]\\d|3[01])|(?:0[469]|11)-(?:0[1-9]|[12]\\d|30)|(?:02)-(?:0[1-9]|1\\d|2[0-8])))";
function anchor(source) {
  return new RegExp(`^${source}$`);
}
var date = /* @__PURE__ */ anchor(dateSource);
function timeSource(args) {
  return typeof args.precision === "number" ? args.precision === -1 ? "(?:[01]\\d|2[0-3]):[0-5]\\d" : args.precision === 0 ? "(?:[01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d" : `(?:[01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d\\.\\d{${args.precision}}` : args.seconds ? "(?:[01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d(?:\\.\\d+)?" : "(?:[01]\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d(?:\\.\\d+)?)?";
}
function time(args) {
  return new RegExp(`^${timeSource(args)}$`);
}
function datetime(args) {
  let opts = ["Z"];
  if (args.offset)
    opts.push("([+-](?:[01]\\d|2[0-3]):[0-5]\\d)");
  let qualified = `${timeSource({ precision: args.precision, seconds: !0 })}(?:${opts.join("|")})`, timeRegex = args.local ? `${qualified}|${timeSource({ precision: args.precision })}` : qualified;
  return new RegExp(`^${dateSource}T(?:${timeRegex})$`);
}
var string = (params) => {
  let regex = params ? `[\\s\\S]{${params?.minimum ?? 0},${params?.maximum ?? ""}}` : "[\\s\\S]*";
  return new RegExp(`^${regex}$`);
};
var integer = /^-?\d+$/, number = /^-?\d+(?:\.\d+)?$/, boolean = /^(?:true|false)$/i, _null = /^null$/i;
var lowercase = /^[^A-Z]*$/, uppercase = /^[^a-z]*$/;

// node_modules/zod/v4/core/checks.js
var $ZodCheck = /* @__PURE__ */ $constructor("$ZodCheck", (inst, def) => {
  var _a;
  inst._zod ?? (inst._zod = {}), inst._zod.def = def, (_a = inst._zod).onattach ?? (_a.onattach = []);
});
var _whenHasLength = (payload) => {
  let val = payload.value;
  return !nullish(val) && val.length !== void 0;
}, numericOriginMap = {
  number: "number",
  bigint: "bigint",
  object: "date"
}, $ZodCheckLessThan = /* @__PURE__ */ $constructor("$ZodCheckLessThan", (inst, def) => {
  $ZodCheck.init(inst, def);
  let origin = numericOriginMap[typeof def.value];
  inst._zod.onattach.push((inst) => {
    let bag = inst._zod.bag, curr = (def.inclusive ? bag.maximum : bag.exclusiveMaximum) ?? Number.POSITIVE_INFINITY;
    if (def.value < curr)
      if (def.inclusive)
        bag.maximum = def.value;
      else
        bag.exclusiveMaximum = def.value;
  }), inst._zod.check = (payload) => {
    if (def.inclusive ? payload.value <= def.value : payload.value < def.value)
      return;
    payload.issues.push({
      origin: numericOriginMap[typeof payload.value] ?? origin,
      code: "too_big",
      maximum: typeof def.value === "object" ? def.value.getTime() : def.value,
      input: payload.value,
      inclusive: def.inclusive,
      inst,
      continue: !def.abort
    });
  };
}), $ZodCheckGreaterThan = /* @__PURE__ */ $constructor("$ZodCheckGreaterThan", (inst, def) => {
  $ZodCheck.init(inst, def);
  let origin = numericOriginMap[typeof def.value];
  inst._zod.onattach.push((inst) => {
    let bag = inst._zod.bag, curr = (def.inclusive ? bag.minimum : bag.exclusiveMinimum) ?? Number.NEGATIVE_INFINITY;
    if (def.value > curr)
      if (def.inclusive)
        bag.minimum = def.value;
      else
        bag.exclusiveMinimum = def.value;
  }), inst._zod.check = (payload) => {
    if (def.inclusive ? payload.value >= def.value : payload.value > def.value)
      return;
    payload.issues.push({
      origin: numericOriginMap[typeof payload.value] ?? origin,
      code: "too_small",
      minimum: typeof def.value === "object" ? def.value.getTime() : def.value,
      input: payload.value,
      inclusive: def.inclusive,
      inst,
      continue: !def.abort
    });
  };
}), $ZodCheckMultipleOf = /* @__PURE__ */ $constructor("$ZodCheckMultipleOf", (inst, def) => {
  $ZodCheck.init(inst, def), inst._zod.onattach.push((inst) => {
    var _a;
    (_a = inst._zod.bag).multipleOf ?? (_a.multipleOf = def.value);
  }), inst._zod.check = (payload) => {
    if (typeof payload.value !== typeof def.value)
      throw Error("Cannot mix number and bigint in multiple_of check.");
    if (typeof payload.value === "bigint" ? def.value !== BigInt(0) && payload.value % def.value === BigInt(0) : floatSafeRemainder(payload.value, def.value) === 0)
      return;
    payload.issues.push({
      origin: typeof payload.value,
      code: "not_multiple_of",
      divisor: def.value,
      input: payload.value,
      inst,
      continue: !def.abort
    });
  };
}), $ZodCheckNumberFormat = /* @__PURE__ */ $constructor("$ZodCheckNumberFormat", (inst, def) => {
  $ZodCheck.init(inst, def), def.format = def.format || "float64";
  let isInt = def.format?.includes("int"), origin = isInt ? "int" : "number", [minimum, maximum] = NUMBER_FORMAT_RANGES[def.format];
  inst._zod.onattach.push((inst) => {
    let bag = inst._zod.bag;
    if (bag.format = def.format, bag.minimum = minimum, bag.maximum = maximum, isInt)
      bag.pattern = integer;
  }), inst._zod.check = (payload) => {
    let input = payload.value;
    if (isInt) {
      if (!Number.isInteger(input)) {
        payload.issues.push({
          expected: origin,
          format: def.format,
          code: "invalid_type",
          continue: !1,
          input,
          inst
        });
        return;
      }
      if (!Number.isSafeInteger(input)) {
        if (input > 0)
          payload.issues.push({
            input,
            code: "too_big",
            maximum: Number.MAX_SAFE_INTEGER,
            note: "Integers must be within the safe integer range.",
            inst,
            origin,
            inclusive: !0,
            continue: !def.abort
          });
        else
          payload.issues.push({
            input,
            code: "too_small",
            minimum: Number.MIN_SAFE_INTEGER,
            note: "Integers must be within the safe integer range.",
            inst,
            origin,
            inclusive: !0,
            continue: !def.abort
          });
        return;
      }
    }
    if (input < minimum)
      payload.issues.push({
        origin: "number",
        input,
        code: "too_small",
        minimum,
        inclusive: !0,
        inst,
        continue: !def.abort
      });
    if (input > maximum)
      payload.issues.push({
        origin: "number",
        input,
        code: "too_big",
        maximum,
        inclusive: !0,
        inst,
        continue: !def.abort
      });
  };
});
var $ZodCheckMaxLength = /* @__PURE__ */ $constructor("$ZodCheckMaxLength", (inst, def) => {
  var _a;
  $ZodCheck.init(inst, def), (_a = inst._zod.def).when ?? (_a.when = _whenHasLength), inst._zod.onattach.push((inst) => {
    let curr = inst._zod.bag.maximum ?? Number.POSITIVE_INFINITY;
    if (def.maximum < curr)
      inst._zod.bag.maximum = def.maximum;
  }), inst._zod.check = (payload) => {
    let input = payload.value, units = input.length;
    if ((typeof input === "string" && units > def.maximum ? codePointLength(input) : units) <= def.maximum)
      return;
    let origin = getLengthableOrigin(input);
    payload.issues.push({
      origin,
      code: "too_big",
      maximum: def.maximum,
      inclusive: !0,
      input,
      inst,
      continue: !def.abort
    });
  };
}), $ZodCheckMinLength = /* @__PURE__ */ $constructor("$ZodCheckMinLength", (inst, def) => {
  var _a;
  $ZodCheck.init(inst, def), (_a = inst._zod.def).when ?? (_a.when = _whenHasLength), inst._zod.onattach.push((inst) => {
    let curr = inst._zod.bag.minimum ?? Number.NEGATIVE_INFINITY;
    if (def.minimum > curr)
      inst._zod.bag.minimum = def.minimum;
  }), inst._zod.check = (payload) => {
    let input = payload.value, units = input.length;
    if ((typeof input === "string" && units >= def.minimum && units < def.minimum * 2 ? codePointLength(input) : units) >= def.minimum)
      return;
    let origin = getLengthableOrigin(input);
    payload.issues.push({
      origin,
      code: "too_small",
      minimum: def.minimum,
      inclusive: !0,
      input,
      inst,
      continue: !def.abort
    });
  };
}), $ZodCheckLengthEquals = /* @__PURE__ */ $constructor("$ZodCheckLengthEquals", (inst, def) => {
  var _a;
  $ZodCheck.init(inst, def), (_a = inst._zod.def).when ?? (_a.when = _whenHasLength), inst._zod.onattach.push((inst) => {
    let bag = inst._zod.bag;
    bag.minimum = def.length, bag.maximum = def.length, bag.length = def.length;
  }), inst._zod.check = (payload) => {
    let input = payload.value, units = input.length, length = typeof input === "string" && units >= def.length && units <= def.length * 2 ? codePointLength(input) : units;
    if (length === def.length)
      return;
    let origin = getLengthableOrigin(input), tooBig = length > def.length;
    payload.issues.push({
      origin,
      ...tooBig ? { code: "too_big", maximum: def.length } : { code: "too_small", minimum: def.length },
      inclusive: !0,
      exact: !0,
      input: payload.value,
      inst,
      continue: !def.abort
    });
  };
}), $ZodCheckStringFormat = /* @__PURE__ */ $constructor("$ZodCheckStringFormat", (inst, def) => {
  var _a, _b;
  if ($ZodCheck.init(inst, def), inst._zod.onattach.push((inst) => {
    let bag = inst._zod.bag;
    if (bag.format = def.format, def.pattern)
      bag.patterns ?? (bag.patterns = /* @__PURE__ */ new Set), bag.patterns.add(def.pattern);
  }), def.pattern)
    (_a = inst._zod).check ?? (_a.check = (payload) => {
      if (def.pattern.lastIndex = 0, def.pattern.test(payload.value))
        return;
      payload.issues.push({
        origin: "string",
        code: "invalid_format",
        format: def.format,
        input: payload.value,
        ...def.pattern ? { pattern: def.pattern.toString() } : {},
        inst,
        continue: !def.abort
      });
    });
  else
    (_b = inst._zod).check ?? (_b.check = () => {});
}), $ZodCheckRegex = /* @__PURE__ */ $constructor("$ZodCheckRegex", (inst, def) => {
  $ZodCheckStringFormat.init(inst, def), inst._zod.check = (payload) => {
    if (def.pattern.lastIndex = 0, def.pattern.test(payload.value))
      return;
    payload.issues.push({
      origin: "string",
      code: "invalid_format",
      format: "regex",
      input: payload.value,
      pattern: def.pattern.toString(),
      inst,
      continue: !def.abort
    });
  };
}), $ZodCheckLowerCase = /* @__PURE__ */ $constructor("$ZodCheckLowerCase", (inst, def) => {
  def.pattern ?? (def.pattern = lowercase), $ZodCheckStringFormat.init(inst, def);
}), $ZodCheckUpperCase = /* @__PURE__ */ $constructor("$ZodCheckUpperCase", (inst, def) => {
  def.pattern ?? (def.pattern = uppercase), $ZodCheckStringFormat.init(inst, def);
}), $ZodCheckIncludes = /* @__PURE__ */ $constructor("$ZodCheckIncludes", (inst, def) => {
  $ZodCheck.init(inst, def);
  let escapedRegex = escapeRegex(def.includes), pattern = new RegExp(typeof def.position === "number" ? `^.{${def.position},}${escapedRegex}` : escapedRegex);
  def.pattern = pattern, inst._zod.onattach.push((inst) => {
    let bag = inst._zod.bag;
    bag.patterns ?? (bag.patterns = /* @__PURE__ */ new Set), bag.patterns.add(pattern);
  }), inst._zod.check = (payload) => {
    if (payload.value.includes(def.includes, def.position))
      return;
    payload.issues.push({
      origin: "string",
      code: "invalid_format",
      format: "includes",
      includes: def.includes,
      input: payload.value,
      inst,
      continue: !def.abort
    });
  };
}), $ZodCheckStartsWith = /* @__PURE__ */ $constructor("$ZodCheckStartsWith", (inst, def) => {
  $ZodCheck.init(inst, def);
  let pattern = new RegExp(`^${escapeRegex(def.prefix)}.*`);
  def.pattern ?? (def.pattern = pattern), inst._zod.onattach.push((inst) => {
    let bag = inst._zod.bag;
    bag.patterns ?? (bag.patterns = /* @__PURE__ */ new Set), bag.patterns.add(pattern);
  }), inst._zod.check = (payload) => {
    if (payload.value.startsWith(def.prefix))
      return;
    payload.issues.push({
      origin: "string",
      code: "invalid_format",
      format: "starts_with",
      prefix: def.prefix,
      input: payload.value,
      inst,
      continue: !def.abort
    });
  };
}), $ZodCheckEndsWith = /* @__PURE__ */ $constructor("$ZodCheckEndsWith", (inst, def) => {
  $ZodCheck.init(inst, def);
  let pattern = new RegExp(`.*${escapeRegex(def.suffix)}$`);
  def.pattern ?? (def.pattern = pattern), inst._zod.onattach.push((inst) => {
    let bag = inst._zod.bag;
    bag.patterns ?? (bag.patterns = /* @__PURE__ */ new Set), bag.patterns.add(pattern);
  }), inst._zod.check = (payload) => {
    if (payload.value.endsWith(def.suffix))
      return;
    payload.issues.push({
      origin: "string",
      code: "invalid_format",
      format: "ends_with",
      suffix: def.suffix,
      input: payload.value,
      inst,
      continue: !def.abort
    });
  };
});
var $ZodCheckOverwrite = /* @__PURE__ */ $constructor("$ZodCheckOverwrite", (inst, def) => {
  $ZodCheck.init(inst, def), inst._zod.check = (payload) => {
    payload.value = def.tx(payload.value);
  };
});

// node_modules/zod/v4/core/doc.js
class Doc {
  constructor(args = [], closed = {}) {
    this.content = [], this.indent = 0, this.args = args, this.closed = closed;
  }
  indented(fn) {
    this.indent += 1, fn(this), this.indent -= 1;
  }
  write(arg) {
    if (typeof arg === "function") {
      arg(this, { execution: "sync" }), arg(this, { execution: "async" });
      return;
    }
    let lines = arg.split(`
`).filter((x) => x), minIndent = Math.min(...lines.map((x) => x.length - x.trimStart().length)), dedented = lines.map((x) => x.slice(minIndent)).map((x) => " ".repeat(this.indent * 2) + x);
    for (let line of dedented)
      this.content.push(line);
  }
  compile() {
    let F = Function, content = this?.content ?? [""];
    return new F(...Object.keys(this.closed), `return function (${this.args.join(", ")}) {
${content.join(`
`)}
};`)(...Object.values(this.closed));
  }
}

// node_modules/zod/v4/core/versions.js
var version = {
  major: 4,
  minor: 5,
  patch: 4
};

// node_modules/zod/v4/core/schemas.js
var $ZodType = /* @__PURE__ */ $constructor("$ZodType", (inst, def) => {
  var _a;
  inst ?? (inst = {}), inst._zod.def = def, inst._zod.bag = inst._zod.bag || {}, inst._zod.version = version;
  let defChecks = inst._zod.def.checks, checks = inst._zod.traits.has("$ZodCheck") ? [inst, ...defChecks ?? []] : defChecks?.length ? [...defChecks] : [];
  for (let ch of checks)
    for (let fn of ch._zod.onattach)
      fn(inst);
  if (checks.length === 0)
    (_a = inst._zod).deferred ?? (_a.deferred = []), inst._zod.deferred?.push(() => {
      inst._zod.run = inst._zod.parse;
    });
  else {
    let runChecks = (payload, checks, ctx) => {
      if (payload.memo)
        return payload;
      let isAborted = aborted(payload), asyncResult;
      for (let ch of checks) {
        if (ch._zod.def.when) {
          if (explicitlyAborted(payload))
            continue;
          if (!ch._zod.def.when(payload))
            continue;
        } else if (isAborted)
          continue;
        let currLen = payload.issues.length, _ = ch._zod.check(payload);
        if (_ instanceof Promise && ctx?.async === !1)
          throw new $ZodAsyncError;
        if (asyncResult || _ instanceof Promise)
          asyncResult = (asyncResult ?? Promise.resolve()).then(async () => {
            if (await _, payload.issues.length === currLen)
              return;
            if (attachSchema(payload.issues, currLen, inst), !isAborted)
              isAborted = aborted(payload, currLen);
          });
        else {
          if (payload.issues.length === currLen)
            continue;
          if (attachSchema(payload.issues, currLen, inst), !isAborted)
            isAborted = aborted(payload, currLen);
        }
      }
      if (asyncResult)
        return asyncResult.then(() => payload);
      return payload;
    }, handleCanaryResult = (canary, payload, ctx) => {
      if (aborted(canary))
        return canary.aborted = !0, canary;
      let checkResult = runChecks(payload, checks, ctx);
      if (checkResult instanceof Promise) {
        if (ctx.async === !1)
          throw new $ZodAsyncError;
        return checkResult.then((checkResult) => inst._zod.parse(checkResult, ctx));
      }
      return inst._zod.parse(checkResult, ctx);
    };
    inst._zod.run = (payload, ctx) => {
      if (ctx.skipChecks)
        return inst._zod.parse(payload, ctx);
      if (ctx.direction === "backward") {
        let canary = inst._zod.parse({ value: payload.value, issues: [] }, { ...ctx, skipChecks: !0 });
        if (canary instanceof Promise)
          return canary.then((canary) => handleCanaryResult(canary, payload, ctx));
        return handleCanaryResult(canary, payload, ctx);
      }
      let result = inst._zod.parse(payload, ctx);
      if (result instanceof Promise) {
        if (ctx.async === !1)
          throw new $ZodAsyncError;
        return result.then((result) => runChecks(result, checks, ctx));
      }
      return runChecks(result, checks, ctx);
    };
  }
}, {
  get "~standard"() {
    return hide(this, "~standard", standardProps(this));
  },
  set "~standard"(value) {
    own(this, "~standard", value);
  }
}), toStandardResult = (r) => r.success ? { value: r.data } : { issues: r.error?.issues };
function standardProps(inst) {
  return {
    validate: (value) => {
      try {
        return toStandardResult(safeParse(inst, value));
      } catch (_) {
        return safeParseAsync(inst, value).then(toStandardResult);
      }
    },
    vendor: "zod",
    version: 1
  };
}
var $ZodString = /* @__PURE__ */ $constructor("$ZodString", (inst, def) => {
  $ZodType.init(inst, def), inst._zod.pattern = [...inst?._zod.bag?.patterns ?? []].pop() ?? string(inst._zod.bag), inst._zod.parse = (payload, _) => {
    if (def.coerce)
      try {
        payload.value = String(payload.value);
      } catch (_) {}
    if (typeof payload.value === "string")
      return payload;
    return payload.issues.push({
      expected: "string",
      code: "invalid_type",
      input: payload.value,
      inst
    }), payload;
  };
}), $ZodStringFormat = /* @__PURE__ */ $constructor("$ZodStringFormat", (inst, def) => {
  $ZodCheckStringFormat.init(inst, def), $ZodString.init(inst, def);
}), $ZodGUID = /* @__PURE__ */ $constructor("$ZodGUID", (inst, def) => {
  def.pattern ?? (def.pattern = guid), $ZodStringFormat.init(inst, def);
}), $ZodUUID = /* @__PURE__ */ $constructor("$ZodUUID", (inst, def) => {
  if (def.version) {
    let v = {
      v1: 1,
      v2: 2,
      v3: 3,
      v4: 4,
      v5: 5,
      v6: 6,
      v7: 7,
      v8: 8
    }[def.version];
    if (v === void 0)
      throw Error(`Invalid UUID version: "${def.version}"`);
    def.pattern ?? (def.pattern = uuid(v));
  } else
    def.pattern ?? (def.pattern = uuid());
  $ZodStringFormat.init(inst, def);
}), $ZodEmail = /* @__PURE__ */ $constructor("$ZodEmail", (inst, def) => {
  def.pattern ?? (def.pattern = email), $ZodStringFormat.init(inst, def);
}), URL_BAD_FORMAT = 1, URL_UNPARSEABLE = 2;
function parseURLObject(trimmed, def) {
  if (!def.normalize && def.protocol?.source === httpProtocol.source && !/^https?:\/\//i.test(trimmed))
    return URL_BAD_FORMAT;
  try {
    return new URL(trimmed);
  } catch {
    return URL_UNPARSEABLE;
  }
}
var asciiTabOrNewline = /[\t\n\r]/g;
function stripTabAndNewline(value) {
  return value.replace(asciiTabOrNewline, "");
}
function urlHostnameOk(url, hostname) {
  return hostname.lastIndex = 0, hostname.test(url.hostname);
}
function urlProtocolOk(url, protocol) {
  return protocol.lastIndex = 0, protocol.test(url.protocol.endsWith(":") ? url.protocol.slice(0, -1) : url.protocol);
}
var $ZodURL = /* @__PURE__ */ $constructor("$ZodURL", (inst, def) => {
  $ZodStringFormat.init(inst, def), inst._zod.check = (payload) => {
    try {
      let trimmed = payload.value.trim(), url = parseURLObject(trimmed, def);
      if (url === URL_BAD_FORMAT) {
        payload.issues.push({
          code: "invalid_format",
          format: "url",
          note: "Invalid URL format",
          input: payload.value,
          inst,
          continue: !def.abort
        });
        return;
      }
      if (url === URL_UNPARSEABLE) {
        payload.issues.push({
          code: "invalid_format",
          format: "url",
          input: payload.value,
          inst,
          continue: !def.abort
        });
        return;
      }
      if (def.hostname && !urlHostnameOk(url, def.hostname))
        payload.issues.push({
          code: "invalid_format",
          format: "url",
          note: "Invalid hostname",
          pattern: def.hostname.source,
          input: payload.value,
          inst,
          continue: !def.abort
        });
      if (def.protocol && !urlProtocolOk(url, def.protocol))
        payload.issues.push({
          code: "invalid_format",
          format: "url",
          note: "Invalid protocol",
          pattern: def.protocol.source,
          input: payload.value,
          inst,
          continue: !def.abort
        });
      payload.value = def.normalize ? url.href : stripTabAndNewline(trimmed);
      return;
    } catch (_) {
      payload.issues.push({
        code: "invalid_format",
        format: "url",
        input: payload.value,
        inst,
        continue: !def.abort
      });
    }
  };
}), $ZodEmoji = /* @__PURE__ */ $constructor("$ZodEmoji", (inst, def) => {
  def.pattern ?? (def.pattern = emoji()), $ZodStringFormat.init(inst, def);
}), $ZodNanoID = /* @__PURE__ */ $constructor("$ZodNanoID", (inst, def) => {
  if (def.length !== void 0 && (!Number.isInteger(def.length) || def.length < 1))
    throw Error(`Invalid nanoid length: ${def.length}`);
  def.pattern ?? (def.pattern = def.length === void 0 ? nanoid : nanoidOfLength(def.length)), $ZodStringFormat.init(inst, def);
}), $ZodCUID = /* @__PURE__ */ $constructor("$ZodCUID", (inst, def) => {
  def.pattern ?? (def.pattern = cuid), $ZodStringFormat.init(inst, def);
}), $ZodCUID2 = /* @__PURE__ */ $constructor("$ZodCUID2", (inst, def) => {
  def.pattern ?? (def.pattern = cuid2), $ZodStringFormat.init(inst, def);
}), $ZodULID = /* @__PURE__ */ $constructor("$ZodULID", (inst, def) => {
  def.pattern ?? (def.pattern = ulid), $ZodStringFormat.init(inst, def);
}), $ZodXID = /* @__PURE__ */ $constructor("$ZodXID", (inst, def) => {
  def.pattern ?? (def.pattern = xid), $ZodStringFormat.init(inst, def);
}), $ZodKSUID = /* @__PURE__ */ $constructor("$ZodKSUID", (inst, def) => {
  def.pattern ?? (def.pattern = ksuid), $ZodStringFormat.init(inst, def);
}), $ZodISODateTime = /* @__PURE__ */ $constructor("$ZodISODateTime", (inst, def) => {
  if (def.pattern ?? (def.pattern = datetime(def)), $ZodStringFormat.init(inst, def), def.local || def.precision === -1)
    inst._zod.bag.laxFormat = !0, inst._zod.onattach.push((s) => {
      s._zod.bag.laxFormat = !0;
    });
}), $ZodISODate = /* @__PURE__ */ $constructor("$ZodISODate", (inst, def) => {
  def.pattern ?? (def.pattern = date), $ZodStringFormat.init(inst, def);
}), $ZodISOTime = /* @__PURE__ */ $constructor("$ZodISOTime", (inst, def) => {
  def.pattern ?? (def.pattern = time(def)), $ZodStringFormat.init(inst, def);
}), $ZodISODuration = /* @__PURE__ */ $constructor("$ZodISODuration", (inst, def) => {
  def.pattern ?? (def.pattern = duration), $ZodStringFormat.init(inst, def);
}), $ZodIPv4 = /* @__PURE__ */ $constructor("$ZodIPv4", (inst, def) => {
  def.pattern ?? (def.pattern = ipv4), $ZodStringFormat.init(inst, def), inst._zod.bag.format = "ipv4";
}), ipv6Alphabet = /^[0-9a-fA-F:.]+$/;
function isValidIPv6(value) {
  if (!ipv6Alphabet.test(value))
    return !1;
  try {
    return new URL(`http://[${value}]`), !0;
  } catch {
    return !1;
  }
}
var $ZodIPv6 = /* @__PURE__ */ $constructor("$ZodIPv6", (inst, def) => {
  def.pattern ?? (def.pattern = ipv6), $ZodStringFormat.init(inst, def), inst._zod.bag.format = "ipv6", inst._zod.check = (payload) => {
    if (!isValidIPv6(payload.value))
      payload.issues.push({
        code: "invalid_format",
        format: "ipv6",
        input: payload.value,
        inst,
        continue: !def.abort
      });
  };
});
var $ZodCIDRv4 = /* @__PURE__ */ $constructor("$ZodCIDRv4", (inst, def) => {
  def.pattern ?? (def.pattern = cidrv4), $ZodStringFormat.init(inst, def);
});
function isValidCIDRv6(value) {
  let parts = value.split("/");
  if (parts.length !== 2)
    return !1;
  let [address, prefix] = parts;
  if (!prefix)
    return !1;
  let prefixNum = Number(prefix);
  if (`${prefixNum}` !== prefix)
    return !1;
  if (prefixNum < 0 || prefixNum > 128)
    return !1;
  return isValidIPv6(address);
}
var $ZodCIDRv6 = /* @__PURE__ */ $constructor("$ZodCIDRv6", (inst, def) => {
  def.pattern ?? (def.pattern = cidrv6), $ZodStringFormat.init(inst, def), inst._zod.check = (payload) => {
    if (!isValidCIDRv6(payload.value))
      payload.issues.push({
        code: "invalid_format",
        format: "cidrv6",
        input: payload.value,
        inst,
        continue: !def.abort
      });
  };
});
function isValidBase64(data) {
  if (data === "")
    return !0;
  if (/\s/.test(data))
    return !1;
  if (data.length % 4 !== 0)
    return !1;
  try {
    return atob(data), !0;
  } catch {
    return !1;
  }
}
var $ZodBase64 = /* @__PURE__ */ $constructor("$ZodBase64", (inst, def) => {
  def.pattern ?? (def.pattern = base64), $ZodStringFormat.init(inst, def), inst._zod.bag.contentEncoding = "base64", inst._zod.check = (payload) => {
    if (isValidBase64(payload.value))
      return;
    payload.issues.push({
      code: "invalid_format",
      format: "base64",
      input: payload.value,
      inst,
      continue: !def.abort
    });
  };
});
function isValidBase64URL(data) {
  if (!base64url.test(data))
    return !1;
  let base64 = data.replace(/[-_]/g, (c) => c === "-" ? "+" : "/"), padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, "=");
  return isValidBase64(padded);
}
var $ZodBase64URL = /* @__PURE__ */ $constructor("$ZodBase64URL", (inst, def) => {
  def.pattern ?? (def.pattern = base64url), $ZodStringFormat.init(inst, def), inst._zod.bag.contentEncoding = "base64url", inst._zod.check = (payload) => {
    if (isValidBase64URL(payload.value))
      return;
    payload.issues.push({
      code: "invalid_format",
      format: "base64url",
      input: payload.value,
      inst,
      continue: !def.abort
    });
  };
}), $ZodE164 = /* @__PURE__ */ $constructor("$ZodE164", (inst, def) => {
  def.pattern ?? (def.pattern = e164), $ZodStringFormat.init(inst, def);
});
function isValidJWT(token, algorithm = null) {
  try {
    let tokensParts = token.split(".");
    if (tokensParts.length !== 3)
      return !1;
    let [header] = tokensParts;
    if (!header)
      return !1;
    let parsedHeader = JSON.parse(atob(header));
    if ("typ" in parsedHeader && parsedHeader?.typ !== "JWT")
      return !1;
    if (!parsedHeader.alg)
      return !1;
    if (algorithm && (!("alg" in parsedHeader) || parsedHeader.alg !== algorithm))
      return !1;
    return !0;
  } catch {
    return !1;
  }
}
var $ZodJWT = /* @__PURE__ */ $constructor("$ZodJWT", (inst, def) => {
  $ZodStringFormat.init(inst, def), inst._zod.check = (payload) => {
    if (isValidJWT(payload.value, def.alg))
      return;
    payload.issues.push({
      code: "invalid_format",
      format: "jwt",
      input: payload.value,
      inst,
      continue: !def.abort
    });
  };
});
var $ZodNumber = /* @__PURE__ */ $constructor("$ZodNumber", (inst, def) => {
  $ZodType.init(inst, def), inst._zod.pattern = inst._zod.bag.pattern ?? number, inst._zod.parse = (payload, _ctx) => {
    if (def.coerce)
      try {
        payload.value = Number(payload.value);
      } catch (_) {}
    let input = payload.value;
    if (typeof input === "number" && !Number.isNaN(input) && Number.isFinite(input))
      return payload;
    let received = typeof input === "number" ? Number.isNaN(input) ? "NaN" : !Number.isFinite(input) ? String(input) : void 0 : void 0;
    return payload.issues.push({
      expected: "number",
      code: "invalid_type",
      input,
      inst,
      ...received ? { received } : {}
    }), payload;
  };
}), $ZodNumberFormat = /* @__PURE__ */ $constructor("$ZodNumberFormat", (inst, def) => {
  $ZodCheckNumberFormat.init(inst, def), $ZodNumber.init(inst, def);
}), $ZodBoolean = /* @__PURE__ */ $constructor("$ZodBoolean", (inst, def) => {
  $ZodType.init(inst, def), inst._zod.pattern = boolean, inst._zod.parse = (payload, _ctx) => {
    if (def.coerce)
      try {
        payload.value = Boolean(payload.value);
      } catch (_) {}
    let input = payload.value;
    if (typeof input === "boolean")
      return payload;
    return payload.issues.push({
      expected: "boolean",
      code: "invalid_type",
      input,
      inst
    }), payload;
  };
});
var $ZodNull = /* @__PURE__ */ $constructor("$ZodNull", (inst, def) => {
  $ZodType.init(inst, def), inst._zod.pattern = _null, inst._zod.values = /* @__PURE__ */ new Set([null]), inst._zod.parse = (payload, _ctx) => {
    let input = payload.value;
    if (input === null)
      return payload;
    return payload.issues.push({
      expected: "null",
      code: "invalid_type",
      input,
      inst
    }), payload;
  };
}), $ZodAny = /* @__PURE__ */ $constructor("$ZodAny", (inst, def) => {
  $ZodType.init(inst, def), inst._zod.parse = (payload) => payload;
}), $ZodUnknown = /* @__PURE__ */ $constructor("$ZodUnknown", (inst, def) => {
  $ZodType.init(inst, def), inst._zod.parse = (payload) => payload;
}), $ZodNever = /* @__PURE__ */ $constructor("$ZodNever", (inst, def) => {
  $ZodType.init(inst, def), inst._zod.parse = (payload, _ctx) => (payload.issues.push({
    expected: "never",
    code: "invalid_type",
    input: payload.value,
    inst
  }), payload);
});
function handleArrayResult(result, final, index) {
  if (result.issues.length)
    final.issues.push(...prefixIssues(index, result.issues));
  final.value[index] = result.value;
}
var $ZodArray = /* @__PURE__ */ $constructor("$ZodArray", (inst, def) => {
  $ZodType.init(inst, def);
  let memo = globalConfig.memoizer;
  memo?.attach(inst), inst._zod.parse = (payload, ctx) => {
    let input = payload.value;
    if (!Array.isArray(input))
      return payload.issues.push({
        expected: "array",
        code: "invalid_type",
        input,
        inst
      }), payload;
    payload.value = memo ? memo.alloc(inst, payload, Array(input.length), ctx) : Array(input.length);
    let proms = [];
    for (let i = 0;i < input.length; i++) {
      let item = input[i], result = def.element._zod.run({
        value: item,
        issues: []
      }, ctx);
      if (result instanceof Promise)
        proms.push(result.then((result) => handleArrayResult(result, payload, i)));
      else
        handleArrayResult(result, payload, i);
    }
    if (proms.length)
      return Promise.all(proms).then(() => payload);
    return payload;
  };
});
function handlePropertyResult(result, final, key, input, optin, optout) {
  let isPresent = key in input, isOptionalOut = optout === "optional";
  if (!isPresent && isOptionalOut && optin === "optional")
    return;
  if (result.issues.length) {
    if (optin !== void 0 && isOptionalOut && !isPresent)
      return;
    final.issues.push(...prefixIssues(key, result.issues));
  }
  if (!isPresent && optin === void 0) {
    if (!result.issues.length)
      final.issues.push({
        code: "invalid_type",
        expected: "nonoptional",
        input: void 0,
        path: [key]
      });
    return;
  }
  if (result.value === void 0) {
    if (isPresent)
      final.value[key] = void 0;
  } else
    final.value[key] = result.value;
}
var NO_SYMBOL_KEYS = [];
function normalizeDef(def) {
  let keys = Object.keys(def.shape), ownSymbols = Object.getOwnPropertySymbols(def.shape), symbolKeys = ownSymbols.length ? ownSymbols : NO_SYMBOL_KEYS, allKeys = symbolKeys.length ? [...keys, ...symbolKeys] : keys;
  for (let k of allKeys)
    if (!def.shape?.[k]?._zod?.traits?.has("$ZodType"))
      throw Error(`Invalid element at key "${String(k)}": expected a Zod schema`);
  let okeys = optionalKeys(def.shape);
  return {
    ...def,
    allKeys,
    symbolKeys,
    keySet: new Set(keys),
    numKeys: keys.length,
    optionalKeys: new Set(okeys)
  };
}
function handleCatchall(proms, input, payload, ctx, def, inst) {
  let unrecognized = [], keySet = def.keySet, _catchall = def.catchall._zod, t = _catchall.def.type, { optin, optout } = _catchall;
  for (let key in input) {
    if (keySet.has(key))
      continue;
    if (key === "__proto__") {
      if (t === "never")
        unrecognized.push(key);
      continue;
    }
    if (t === "never") {
      unrecognized.push(key);
      continue;
    }
    let r = _catchall.run({ value: input[key], issues: [] }, ctx);
    if (r instanceof Promise)
      proms.push(r.then((r) => handlePropertyResult(r, payload, key, input, optin, optout)));
    else
      handlePropertyResult(r, payload, key, input, optin, optout);
  }
  if (unrecognized.length)
    payload.issues.push({
      code: "unrecognized_keys",
      keys: unrecognized,
      input,
      inst,
      continue: !0
    });
  if (!proms.length)
    return payload;
  return Promise.all(proms).then(() => payload);
}
var propShapes = /* @__PURE__ */ new WeakMap, $ZodObject = /* @__PURE__ */ $constructor("$ZodObject", (inst, def) => {
  if ($ZodType.init(inst, def), !Object.getOwnPropertyDescriptor(def, "shape")?.get) {
    let sh = def.shape;
    propShapes.set(def, sh), Object.defineProperty(def, "shape", {
      get: () => {
        let newSh = { ...sh };
        return Object.defineProperty(def, "shape", {
          value: newSh
        }), propShapes.set(def, newSh), newSh;
      }
    });
  }
  let _normalized = cached(() => normalizeDef(def));
  defineLazyInternal(inst, "propValues", (zod) => {
    let shape = zod.def.shape, propValues = {};
    for (let key in shape) {
      let field = shape[key]._zod;
      if (field.values) {
        if (!Object.prototype.hasOwnProperty.call(propValues, key))
          assignProp(propValues, key, /* @__PURE__ */ new Set);
        for (let v of field.values)
          propValues[key].add(v);
        if (field.optin !== void 0)
          propValues[key].add(void 0);
      }
    }
    return propValues;
  });
  let isObject2 = isObject, catchall = def.catchall, value, memo = globalConfig.memoizer;
  memo?.attach(inst), inst._zod.parse = (payload, ctx) => {
    value ?? (value = _normalized.value);
    let input = payload.value;
    if (!isObject2(input))
      return payload.issues.push({
        expected: "object",
        code: "invalid_type",
        input,
        inst
      }), payload;
    payload.value = memo ? memo.alloc(inst, payload, {}, ctx) : {};
    let proms = [], shape = value.shape;
    for (let key of value.allKeys) {
      if (key === "__proto__")
        continue;
      let el = shape[key], optin = el._zod.optin, optout = el._zod.optout, r = el._zod.run({ value: input[key], issues: [] }, ctx);
      if (r instanceof Promise)
        proms.push(r.then((r) => handlePropertyResult(r, payload, key, input, optin, optout)));
      else
        handlePropertyResult(r, payload, key, input, optin, optout);
    }
    if (!catchall)
      return proms.length ? Promise.all(proms).then(() => payload) : payload;
    return handleCatchall(proms, input, payload, ctx, _normalized.value, inst);
  };
}), $ZodObjectJIT = /* @__PURE__ */ $constructor("$ZodObjectJIT", (inst, def) => {
  $ZodObject.init(inst, def);
  let superParse = inst._zod.parse, _normalized = cached(() => normalizeDef(def)), memo = globalConfig.memoizer, generateFastpass = (shape) => {
    let normalized = _normalized.value, syms = normalized.symbolKeys, doc = new Doc(["payload", "ctx"], { shape, inst, memo, syms }), parseStr = (k) => `shape[${k}]._zod.run({ value: input[${k}], issues: [] }, ctx)`, prefixStr = (id, k) => `
          for (let i = 0; i < ${id}.issues.length; i++) {
            const iss = ${id}.issues[i];
            iss.path = iss.path ? [${k}, ...iss.path] : [${k}];
            payload.issues.push(iss);
          }`;
    doc.write("const input = payload.value;");
    let ids = Object.create(null), counter = 0;
    for (let key of normalized.allKeys)
      ids[key] = `key_${counter++}`;
    doc.write(memo ? "const newResult = memo.alloc(inst, payload, {}, ctx);" : "const newResult = {};");
    for (let key of normalized.allKeys) {
      if (key === "__proto__")
        continue;
      let id = ids[key], k = typeof key === "symbol" ? `syms[${syms.indexOf(key)}]` : esc(key), isPresent = `${k} in input`, schema = shape[key], optin = schema?._zod?.optin, isOptionalIn = optin !== void 0, isOptionalOut = schema?._zod?.optout === "optional";
      if (doc.write(`const ${id} = ${parseStr(k)};`), isOptionalIn && isOptionalOut) {
        let assign = optin === "optional" ? `${id}_present` : `${id}.value !== undefined || ${id}_present`;
        doc.write(`
        const ${id}_present = ${isPresent};
        if (!${id}.issues.length || ${id}_present) {
          if (${id}.issues.length) {${prefixStr(id, k)}
          }

          if (${assign}) {
            newResult[${k}] = ${id}.value;
          }
        }

      `);
      } else if (!isOptionalIn)
        doc.write(`
        const ${id}_present = ${isPresent};
        if (${id}.issues.length) {${prefixStr(id, k)}
        }
        if (!${id}_present && !${id}.issues.length) {
          payload.issues.push({
            code: "invalid_type",
            expected: "nonoptional",
            input: undefined,
            path: [${k}]
          });
        }

        if (${id}_present) {
          newResult[${k}] = ${id}.value;
        }

      `);
      else
        doc.write(`
        if (${id}.issues.length) {${prefixStr(id, k)}
        }
        
        if (${id}.value === undefined) {
          if (${isPresent}) {
            newResult[${k}] = undefined;
          }
        } else {
          newResult[${k}] = ${id}.value;
        }

      `);
    }
    return doc.write("payload.value = newResult;"), doc.write("return payload;"), doc.compile();
  }, fastpass, isObject2 = isObject, jit = !globalConfig.jitless, fastEnabled = jit && allowsEval.value, catchall = def.catchall, value;
  inst._zod.parse = (payload, ctx) => {
    value ?? (value = _normalized.value);
    let input = payload.value;
    if (!isObject2(input))
      return payload.issues.push({
        expected: "object",
        code: "invalid_type",
        input,
        inst
      }), payload;
    if (jit && fastEnabled && ctx?.async === !1 && ctx.jitless !== !0) {
      if (!fastpass)
        fastpass = generateFastpass(def.shape);
      if (payload = fastpass(payload, ctx), !catchall)
        return payload;
      return handleCatchall([], input, payload, ctx, value, inst);
    }
    return superParse(payload, ctx);
  };
});
function handleUnionResults(results, final, inst, ctx) {
  for (let result of results)
    if (result.issues.length === 0)
      return final.value = result.value, final;
  let nonaborted = results.filter((r) => !aborted(r));
  if (nonaborted.length === 1)
    return final.value = nonaborted[0].value, nonaborted[0];
  return final.issues.push({
    code: "invalid_union",
    input: final.value,
    inst,
    errors: results.map((result) => result.issues.map((iss) => finalizeIssue(iss, ctx, config())))
  }), final;
}
var $ZodUnion = /* @__PURE__ */ $constructor("$ZodUnion", (inst, def) => {
  $ZodType.init(inst, def), defineLazyInternal(inst, "optin", (zod) => zod.def.options.some((o) => o._zod.optin === "defaulted") ? "defaulted" : zod.def.options.some((o) => o._zod.optin !== void 0) ? "optional" : void 0), defineLazyInternal(inst, "optout", (zod) => zod.def.options.some((o) => o._zod.optout === "optional") ? "optional" : void 0), defineLazyInternal(inst, "values", (zod) => {
    if (zod.def.options.every((o) => o._zod.values))
      return new Set(zod.def.options.flatMap((option) => Array.from(option._zod.values)));
    return;
  }), defineLazyInternal(inst, "pattern", (zod) => {
    if (zod.def.options.every((o) => o._zod.pattern)) {
      let patterns = zod.def.options.map((o) => o._zod.pattern);
      return new RegExp(`^(${patterns.map((p) => cleanRegex(p.source)).join("|")})$`);
    }
    return;
  });
  let first = def.options.length === 1 ? def.options[0]._zod.run : null;
  inst._zod.parse = (payload, ctx) => {
    if (first)
      return first(payload, ctx);
    let async = !1, results = [];
    for (let option of def.options) {
      let result = option._zod.run({
        value: payload.value,
        issues: []
      }, ctx);
      if (result instanceof Promise)
        results.push(result), async = !0;
      else {
        if (result.issues.length === 0)
          return result;
        results.push(result);
      }
    }
    if (!async)
      return handleUnionResults(results, payload, inst, ctx);
    return Promise.all(results).then((results) => handleUnionResults(results, payload, inst, ctx));
  };
});
var $ZodDiscriminatedUnion = /* @__PURE__ */ $constructor("$ZodDiscriminatedUnion", (inst, def) => {
  def.inclusive = !1, $ZodUnion.init(inst, def);
  let _super = inst._zod.parse;
  defineLazyInternal(inst, "propValues", (zod) => {
    let propValues = {};
    for (let option of zod.def.options) {
      let pv = option._zod.propValues;
      if (!pv || Object.keys(pv).length === 0)
        throw Error(`Invalid discriminated union option at index "${zod.def.options.indexOf(option)}"`);
      for (let [k, v] of Object.entries(pv)) {
        if (!Object.prototype.hasOwnProperty.call(propValues, k))
          assignProp(propValues, k, /* @__PURE__ */ new Set);
        for (let val of v)
          propValues[k].add(val);
      }
    }
    return propValues;
  }), def.options.forEach((option, i) => {
    let propShape = propShapes.get(option._zod.def);
    if (propShape && !Object.prototype.hasOwnProperty.call(propShape, def.discriminator))
      throw Error(`Invalid discriminated union option at index "${i}"`);
  });
  let disc = cached(() => {
    let opts = def.options, map = /* @__PURE__ */ new Map;
    for (let o of opts) {
      let values = o._zod.propValues?.[def.discriminator];
      if (!values || values.size === 0)
        throw Error(`Invalid discriminated union option at index "${def.options.indexOf(o)}"`);
      for (let v of values) {
        if (map.has(v))
          throw Error(`Duplicate discriminator value "${String(v)}"`);
        map.set(v, o);
      }
    }
    return map;
  });
  inst._zod.parse = (payload, ctx) => {
    let input = payload.value;
    if (!isObject(input))
      return payload.issues.push({
        code: "invalid_type",
        expected: "object",
        input,
        inst
      }), payload;
    let opt = disc.value.get(input?.[def.discriminator]);
    if (opt)
      return opt._zod.run(payload, ctx);
    if (def.unionFallback || ctx.direction === "backward")
      return _super(payload, ctx);
    return payload.issues.push({
      code: "invalid_union",
      errors: [],
      note: "No matching discriminator",
      discriminator: def.discriminator,
      options: Array.from(disc.value.keys()),
      input,
      path: [def.discriminator],
      inst
    }), payload;
  };
}), $ZodIntersection = /* @__PURE__ */ $constructor("$ZodIntersection", (inst, def) => {
  $ZodType.init(inst, def), inst._zod.parse = (payload, ctx) => {
    let input = payload.value, left = def.left._zod.run({ value: input, issues: [] }, ctx), right = def.right._zod.run({ value: input, issues: [] }, ctx);
    if (left instanceof Promise || right instanceof Promise)
      return Promise.all([left, right]).then(([left, right]) => handleIntersectionResults(payload, left, right));
    return handleIntersectionResults(payload, left, right);
  };
});
function mergeValues(a, b) {
  if (a === b)
    return { valid: !0, data: a };
  if (a instanceof Date && b instanceof Date && +a === +b)
    return { valid: !0, data: a };
  if (isPlainObject(a) && isPlainObject(b)) {
    let bKeys = Object.keys(b), sharedKeys = Object.keys(a).filter((key) => bKeys.indexOf(key) !== -1), newObj = { ...a, ...b };
    if (Object.prototype.hasOwnProperty.call(newObj, "__proto__"))
      delete newObj.__proto__;
    for (let key of sharedKeys) {
      if (key === "__proto__")
        continue;
      let sharedValue = mergeValues(a[key], b[key]);
      if (!sharedValue.valid)
        return {
          valid: !1,
          mergeErrorPath: [key, ...sharedValue.mergeErrorPath]
        };
      newObj[key] = sharedValue.data;
    }
    return { valid: !0, data: newObj };
  }
  if (Array.isArray(a) && Array.isArray(b)) {
    if (a.length !== b.length)
      return { valid: !1, mergeErrorPath: [] };
    let newArray = [];
    for (let index = 0;index < a.length; index++) {
      let itemA = a[index], itemB = b[index], sharedValue = mergeValues(itemA, itemB);
      if (!sharedValue.valid)
        return {
          valid: !1,
          mergeErrorPath: [index, ...sharedValue.mergeErrorPath]
        };
      newArray.push(sharedValue.data);
    }
    return { valid: !0, data: newArray };
  }
  return { valid: !1, mergeErrorPath: [] };
}
function handleIntersectionResults(result, left, right) {
  let unrecKeys = /* @__PURE__ */ new Map, unrecIssue, keyIssues = /* @__PURE__ */ new Map, collect = (iss, side) => {
    let keys;
    if (iss.code === "unrecognized_keys" && !iss.path?.length)
      unrecIssue ?? (unrecIssue = iss), keys = iss.keys;
    else if (iss.code === "invalid_key" && iss.origin === "record" && iss.path?.length === 1) {
      let k = String(iss.path[0]);
      if (!keyIssues.has(k))
        keyIssues.set(k, iss);
      keys = [k];
    } else
      return !1;
    for (let k of keys) {
      if (!unrecKeys.has(k))
        unrecKeys.set(k, {});
      unrecKeys.get(k)[side] = !0;
    }
    return !0;
  };
  for (let iss of left.issues)
    if (!collect(iss, "l"))
      result.issues.push(iss);
  for (let iss of right.issues)
    if (!collect(iss, "r"))
      result.issues.push(iss);
  let bothKeys = [...unrecKeys].filter(([, f]) => f.l && f.r).map(([k]) => k);
  if (bothKeys.length) {
    let aggregated = unrecIssue ? bothKeys.filter((k) => unrecIssue.keys.includes(k)) : [];
    if (aggregated.length)
      result.issues.push({ ...unrecIssue, keys: aggregated });
    for (let k of bothKeys)
      if (!aggregated.includes(k) && keyIssues.has(k))
        result.issues.push(keyIssues.get(k));
  }
  let merged = mergeValues(left.value, right.value);
  if (!merged.valid) {
    if (aborted(result))
      return result;
    throw Error(`Unmergable intersection. Error path: ${JSON.stringify(merged.mergeErrorPath)}`);
  }
  return result.value = merged.data, result;
}
var $ZodRecord = /* @__PURE__ */ $constructor("$ZodRecord", (inst, def) => {
  $ZodType.init(inst, def);
  let memo = globalConfig.memoizer;
  memo?.attach(inst), inst._zod.parse = (payload, ctx) => {
    let input = payload.value;
    if (!isPlainObject(input))
      return payload.issues.push({
        expected: "record",
        code: "invalid_type",
        input,
        inst
      }), payload;
    let proms = [], values = def.keyType._zod.values;
    if (values && !def.partial) {
      payload.value = memo ? memo.alloc(inst, payload, {}, ctx) : {};
      let recordKeys = /* @__PURE__ */ new Set;
      for (let key of values)
        if (typeof key === "string" || typeof key === "number" || typeof key === "symbol") {
          if (recordKeys.add(typeof key === "number" ? key.toString() : key), key === "__proto__")
            continue;
          let keyResult = def.keyType._zod.run({ value: key, issues: [] }, ctx);
          if (keyResult instanceof Promise)
            throw Error("Async schemas not supported in object keys currently");
          if (keyResult.issues.length) {
            payload.issues.push({
              code: "invalid_key",
              origin: "record",
              issues: keyResult.issues.map((iss) => finalizeIssue(iss, ctx, config())),
              input: key,
              path: [key],
              inst
            });
            continue;
          }
          let outKey = keyResult.value;
          if (outKey === "__proto__")
            continue;
          let result = def.valueType._zod.run({ value: input[key], issues: [] }, ctx);
          if (result instanceof Promise)
            proms.push(result.then((result) => {
              if (result.issues.length)
                payload.issues.push(...prefixIssues(key, result.issues));
              payload.value[outKey] = result.value;
            }));
          else {
            if (result.issues.length)
              payload.issues.push(...prefixIssues(key, result.issues));
            payload.value[outKey] = result.value;
          }
        }
      let unrecognized;
      for (let key in input)
        if (!recordKeys.has(key))
          if (def.mode === "loose") {
            if (key === "__proto__")
              continue;
            payload.value[key] = input[key];
          } else
            unrecognized = unrecognized ?? [], unrecognized.push(key);
      if (unrecognized && unrecognized.length > 0)
        payload.issues.push({
          code: "unrecognized_keys",
          input,
          inst,
          keys: unrecognized,
          continue: !0
        });
    } else {
      payload.value = memo ? memo.alloc(inst, payload, {}, ctx) : {};
      let unrecognized;
      for (let key of Reflect.ownKeys(input)) {
        if (key === "__proto__")
          continue;
        if (!Object.prototype.propertyIsEnumerable.call(input, key))
          continue;
        let keyResult = def.keyType._zod.run({ value: key, issues: [] }, ctx);
        if (keyResult instanceof Promise)
          throw Error("Async schemas not supported in object keys currently");
        if (typeof key === "string" && number.test(key) && keyResult.issues.length) {
          let retryResult = def.keyType._zod.run({ value: Number(key), issues: [] }, ctx);
          if (retryResult instanceof Promise)
            throw Error("Async schemas not supported in object keys currently");
          if (retryResult.issues.length === 0)
            keyResult = retryResult;
        }
        if (keyResult.issues.length) {
          if (def.mode === "loose")
            payload.value[key] = input[key];
          else if (values)
            unrecognized = unrecognized ?? [], unrecognized.push(key);
          else
            payload.issues.push({
              code: "invalid_key",
              origin: "record",
              issues: keyResult.issues.map((iss) => finalizeIssue(iss, ctx, config())),
              input: key,
              path: [key],
              inst
            });
          continue;
        }
        let outKey = keyResult.value;
        if (outKey === "__proto__")
          continue;
        let result = def.valueType._zod.run({ value: input[key], issues: [] }, ctx);
        if (result instanceof Promise)
          proms.push(result.then((result) => {
            if (result.issues.length)
              payload.issues.push(...prefixIssues(key, result.issues));
            payload.value[outKey] = result.value;
          }));
        else {
          if (result.issues.length)
            payload.issues.push(...prefixIssues(key, result.issues));
          payload.value[outKey] = result.value;
        }
      }
      if (unrecognized && unrecognized.length > 0)
        payload.issues.push({
          code: "unrecognized_keys",
          input,
          inst,
          keys: unrecognized,
          continue: !0
        });
    }
    if (proms.length)
      return Promise.all(proms).then(() => payload);
    return payload;
  };
});
var $ZodEnum = /* @__PURE__ */ $constructor("$ZodEnum", (inst, def) => {
  $ZodType.init(inst, def);
  let values = getEnumValues(def.entries), valuesSet = new Set(values);
  inst._zod.values = valuesSet;
  let patternValues = values.filter((k) => propertyKeyTypes.has(typeof k));
  inst._zod.pattern = new RegExp(patternValues.length ? `^(${patternValues.map((o) => escapeRegex(o.toString())).join("|")})$` : "^[^\\s\\S]$"), inst._zod.parse = (payload, _ctx) => {
    let input = payload.value;
    if (valuesSet.has(input))
      return payload;
    return payload.issues.push({
      code: "invalid_value",
      values,
      input,
      inst
    }), payload;
  };
}), $ZodLiteral = /* @__PURE__ */ $constructor("$ZodLiteral", (inst, def) => {
  $ZodType.init(inst, def);
  let values = new Set(def.values);
  inst._zod.values = values, inst._zod.pattern = new RegExp(def.values.length ? `^(${def.values.map((o) => typeof o === "string" ? escapeRegex(o) : o ? escapeRegex(o.toString()) : String(o)).join("|")})$` : "^[^\\s\\S]$"), inst._zod.parse = (payload, _ctx) => {
    let input = payload.value;
    if (values.has(input))
      return payload;
    return payload.issues.push({
      code: "invalid_value",
      values: def.values,
      input,
      inst
    }), payload;
  };
});
var $ZodTransform = /* @__PURE__ */ $constructor("$ZodTransform", (inst, def) => {
  $ZodType.init(inst, def), inst._zod.optin = "optional", globalConfig.memoizer?.guard(inst), inst._zod.parse = (payload, ctx) => {
    if (ctx.direction === "backward")
      throw new $ZodEncodeError(inst.constructor.name);
    let _out = def.transform(payload.value, payload);
    if (ctx.async)
      return (_out instanceof Promise ? _out : Promise.resolve(_out)).then((output) => (payload.value = output, payload));
    if (_out instanceof Promise)
      throw new $ZodAsyncError;
    return payload.value = _out, payload;
  };
});
function handleOptionalResult(payload, result) {
  return payload.value = result.issues.length ? void 0 : result.value, payload;
}
var $ZodOptional = /* @__PURE__ */ $constructor("$ZodOptional", (inst, def) => {
  $ZodType.init(inst, def), defineLazyInternal(inst, "optin", (zod) => zod.def.innerType._zod.optin === "defaulted" ? "defaulted" : "optional"), inst._zod.optout = "optional", defineLazyInternal(inst, "values", (zod) => {
    let values = zod.def.innerType._zod.values;
    return values ? /* @__PURE__ */ new Set([...values, void 0]) : void 0;
  }), defineLazyInternal(inst, "pattern", (zod) => {
    let pattern = zod.def.innerType._zod.pattern;
    return pattern ? new RegExp(`^(${cleanRegex(pattern.source)})?$`) : void 0;
  }), inst._zod.parse = (payload, ctx) => {
    if (payload.value === void 0) {
      if (def.innerType._zod.optin !== "defaulted")
        return payload;
      let result = def.innerType._zod.run({ value: payload.value, issues: [] }, ctx);
      if (result instanceof Promise)
        return result.then((result) => handleOptionalResult(payload, result));
      return handleOptionalResult(payload, result);
    }
    return def.innerType._zod.run(payload, ctx);
  };
}), $ZodExactOptional = /* @__PURE__ */ $constructor("$ZodExactOptional", (inst, def) => {
  $ZodOptional.init(inst, def), defineLazyInternal(inst, "values", (zod) => zod.def.innerType._zod.values), defineLazyInternal(inst, "pattern", (zod) => zod.def.innerType._zod.pattern), inst._zod.parse = (payload, ctx) => def.innerType._zod.run(payload, ctx);
}), $ZodNullable = /* @__PURE__ */ $constructor("$ZodNullable", (inst, def) => {
  $ZodType.init(inst, def), defineLazyInternal(inst, "optin", (zod) => zod.def.innerType._zod.optin), defineLazyInternal(inst, "optout", (zod) => zod.def.innerType._zod.optout), defineLazyInternal(inst, "pattern", (zod) => {
    let pattern = zod.def.innerType._zod.pattern;
    return pattern ? new RegExp(`^(${cleanRegex(pattern.source)}|null)$`) : void 0;
  }), defineLazyInternal(inst, "values", (zod) => zod.def.innerType._zod.values ? /* @__PURE__ */ new Set([...zod.def.innerType._zod.values, null]) : void 0), inst._zod.parse = (payload, ctx) => {
    if (payload.value === null)
      return payload;
    return def.innerType._zod.run(payload, ctx);
  };
}), $ZodDefault = /* @__PURE__ */ $constructor("$ZodDefault", (inst, def) => {
  $ZodType.init(inst, def), inst._zod.optin = "defaulted", defineLazyInternal(inst, "values", (zod) => zod.def.innerType._zod.values), inst._zod.parse = (payload, ctx) => {
    if (ctx.direction === "backward")
      return def.innerType._zod.run(payload, ctx);
    if (payload.value === void 0)
      return payload.value = def.defaultValue, payload;
    let result = def.innerType._zod.run(payload, ctx);
    if (result instanceof Promise)
      return result.then((result) => handleDefaultResult(result, def));
    return handleDefaultResult(result, def);
  };
});
function handleDefaultResult(payload, def) {
  if (payload.value === void 0)
    payload.value = def.defaultValue;
  return payload;
}
var $ZodPrefault = /* @__PURE__ */ $constructor("$ZodPrefault", (inst, def) => {
  $ZodType.init(inst, def), inst._zod.optin = "defaulted", defineLazyInternal(inst, "values", (zod) => zod.def.innerType._zod.values), inst._zod.parse = (payload, ctx) => {
    if (ctx.direction === "backward")
      return def.innerType._zod.run(payload, ctx);
    if (payload.value === void 0)
      payload.value = def.defaultValue;
    return def.innerType._zod.run(payload, ctx);
  };
}), $ZodNonOptional = /* @__PURE__ */ $constructor("$ZodNonOptional", (inst, def) => {
  $ZodType.init(inst, def), defineLazyInternal(inst, "values", (zod) => {
    let v = zod.def.innerType._zod.values;
    return v ? new Set([...v].filter((x) => x !== void 0)) : void 0;
  }), inst._zod.parse = (payload, ctx) => {
    let result = def.innerType._zod.run(payload, ctx);
    if (result instanceof Promise)
      return result.then((result) => handleNonOptionalResult(result, inst));
    return handleNonOptionalResult(result, inst);
  };
});
function handleNonOptionalResult(payload, inst) {
  if (!payload.issues.length && payload.value === void 0)
    payload.issues.push({
      code: "invalid_type",
      expected: "nonoptional",
      input: payload.value,
      inst
    });
  return payload;
}
function handleCatchResult(payload, result, def, ctx) {
  if (!result.issues.length) {
    if (payload.value = result.value, result.memo)
      payload.memo = !0;
    return payload;
  }
  return payload.value = def.catchValue({
    ...result,
    value: payload.value,
    error: {
      issues: result.issues.map((iss) => finalizeIssue(iss, ctx, config()))
    },
    input: payload.value
  }), payload;
}
var $ZodCatch = /* @__PURE__ */ $constructor("$ZodCatch", (inst, def) => {
  $ZodType.init(inst, def), defineLazyInternal(inst, "optin", (zod) => zod.def.innerType._zod.optin === "defaulted" ? "defaulted" : "optional"), defineLazyInternal(inst, "optout", (zod) => zod.def.innerType._zod.optout), defineLazyInternal(inst, "values", (zod) => zod.def.innerType._zod.values), inst._zod.parse = (payload, ctx) => {
    if (ctx.direction === "backward")
      return def.innerType._zod.run(payload, ctx);
    let result = def.innerType._zod.run({ value: payload.value, issues: [] }, ctx);
    if (result instanceof Promise)
      return result.then((result) => handleCatchResult(payload, result, def, ctx));
    return handleCatchResult(payload, result, def, ctx);
  };
});
var $ZodPipe = /* @__PURE__ */ $constructor("$ZodPipe", (inst, def) => {
  $ZodType.init(inst, def), defineLazyInternal(inst, "values", (zod) => zod.def.in._zod.values), defineLazyInternal(inst, "optin", (zod) => zod.def.in._zod.optin), defineLazyInternal(inst, "optout", (zod) => zod.def.out._zod.optout), defineLazyInternal(inst, "propValues", (zod) => zod.def.in._zod.propValues), inst._zod.parse = (payload, ctx) => {
    if (ctx.direction === "backward") {
      let right = def.out._zod.run(payload, ctx);
      if (right instanceof Promise)
        return right.then((right) => handlePipeResult(right, def.in, ctx));
      return handlePipeResult(right, def.in, ctx);
    }
    let left = def.in._zod.run(payload, ctx);
    if (left instanceof Promise)
      return left.then((left) => handlePipeResult(left, def.out, ctx));
    return handlePipeResult(left, def.out, ctx);
  };
});
function handlePipeResult(left, next, ctx) {
  if (left.issues.some((iss) => iss.code !== "unrecognized_keys"))
    return left.aborted = !0, left;
  return next._zod.run({ value: left.value, issues: left.issues }, ctx);
}
var $ZodPreprocess = /* @__PURE__ */ $constructor("$ZodPreprocess", (inst, def) => {
  $ZodPipe.init(inst, def);
}), $ZodReadonly = /* @__PURE__ */ $constructor("$ZodReadonly", (inst, def) => {
  $ZodType.init(inst, def), defineLazyInternal(inst, "propValues", (zod) => zod.def.innerType._zod.propValues), defineLazyInternal(inst, "values", (zod) => zod.def.innerType._zod.values), defineLazyInternal(inst, "optin", (zod) => zod.def.innerType?._zod?.optin), defineLazyInternal(inst, "optout", (zod) => zod.def.innerType?._zod?.optout), inst._zod.parse = (payload, ctx) => {
    if (ctx.direction === "backward")
      return def.innerType._zod.run(payload, ctx);
    let result = def.innerType._zod.run(payload, ctx);
    if (result instanceof Promise)
      return result.then(handleReadonlyResult);
    return handleReadonlyResult(result);
  };
});
function handleReadonlyResult(payload) {
  if (!payload.memo)
    payload.value = Object.freeze(payload.value);
  return payload;
}
var $ZodCustom = /* @__PURE__ */ $constructor("$ZodCustom", (inst, def) => {
  $ZodCheck.init(inst, def), $ZodType.init(inst, def), inst._zod.parse = (payload, _) => payload, inst._zod.check = (payload) => {
    let input = payload.value, r = def.fn(input);
    if (r instanceof Promise)
      return r.then((r) => handleRefineResult(r, payload, input, inst));
    handleRefineResult(r, payload, input, inst);
    return;
  };
});
function handleRefineResult(result, payload, input, inst) {
  if (!result) {
    let _iss = {
      code: "custom",
      input,
      inst,
      path: [...inst._zod.def.path ?? []],
      continue: !inst._zod.def.abort
    };
    if (inst._zod.def.params)
      _iss.params = inst._zod.def.params;
    payload.issues.push(issue(_iss));
  }
}
// node_modules/zod/v4/core/memoizer.js
class $ZodCyclicError extends Error {
  constructor() {
    super("Cannot parse a reference cycle that closes through a transform");
    this.name = "ZodCyclicError";
  }
}
var STATE = "~memo", NO_ISSUES = [];
function cloneIssues(issues) {
  return issues.map((iss) => iss.path ? { ...iss, path: iss.path.slice() } : { ...iss });
}
var recursive = /* @__PURE__ */ new WeakMap;
function isRecursive(inst, stack) {
  let cached = recursive.get(inst);
  if (cached !== void 0)
    return cached;
  if (stack.has(inst))
    return !0;
  stack.add(inst);
  let result = !1, check = (child) => {
    if (!result && child?._zod && isRecursive(child, stack))
      result = !0;
  }, def = inst._zod.def, kind = def.type;
  switch (kind) {
    case "object": {
      for (let key of Reflect.ownKeys(def.shape))
        check(def.shape[key]);
      check(def.catchall);
      break;
    }
    case "array":
      check(def.element);
      break;
    case "tuple":
      for (let el of def.items)
        check(el);
      check(def.rest);
      break;
    case "record":
    case "map":
      check(def.keyType), check(def.valueType);
      break;
    case "set":
      check(def.valueType);
      break;
    case "union":
      for (let el of def.options)
        check(el);
      break;
    case "intersection":
      check(def.left), check(def.right);
      break;
    case "optional":
    case "nullable":
    case "default":
    case "prefault":
    case "catch":
    case "readonly":
    case "nonoptional":
    case "promise":
    case "success":
      check(def.innerType);
      break;
    case "pipe":
      check(def.in), check(def.out);
      break;
    case "function":
      check(def.input), check(def.output);
      break;
    case "lazy":
      check(inst._zod.innerType);
      break;
    case "template_literal":
    case "string":
    case "number":
    case "int":
    case "boolean":
    case "bigint":
    case "symbol":
    case "undefined":
    case "null":
    case "void":
    case "never":
    case "any":
    case "unknown":
    case "date":
    case "nan":
    case "enum":
    case "literal":
    case "file":
    case "transform":
    case "custom":
      break;
    default:
      for (let key in def) {
        let desc = Object.getOwnPropertyDescriptor(def, key);
        if (!desc || desc.get)
          continue;
        let value = desc.value;
        if (!value || typeof value !== "object")
          continue;
        if (value._zod)
          check(value);
        else if (Array.isArray(value))
          for (let el of value)
            check(el);
      }
  }
  return stack.delete(inst), recursive.set(inst, result), result;
}
function bucketFor(state, inst) {
  let bucket = state.buckets.get(inst);
  if (!bucket)
    bucket = /* @__PURE__ */ new Map, state.buckets.set(inst, bucket);
  return bucket;
}
var handoff, open = [], memo = {
  alloc(_inst, payload, empty) {
    let bucket = handoff;
    if (!bucket)
      return empty;
    handoff = void 0;
    let entry = { value: empty, issues: null };
    return bucket.set(payload.value, entry), open.push(entry), empty;
  },
  guard(inst) {
    var _a;
    (_a = inst._zod).deferred ?? (_a.deferred = []), inst._zod.deferred.push(() => {
      let base = inst._zod.parse, wrapped = (payload, ctx) => {
        if (ctx.direction !== "backward" && isBackEdge(ctx, payload.value))
          throw new $ZodCyclicError;
        return base(payload, ctx);
      };
      if (inst._zod.parse = wrapped, inst._zod.run === base)
        inst._zod.run = wrapped;
    });
  },
  attach(inst) {
    var _a;
    let isRecursiveInst, lastCtx, lastBucket;
    (_a = inst._zod).deferred ?? (_a.deferred = []), inst._zod.deferred.push(() => {
      let base = inst._zod.parse, wrapped = (payload, ctx) => {
        if (isRecursiveInst === void 0) {
          if (isRecursiveInst = isRecursive(inst, /* @__PURE__ */ new Set), !isRecursiveInst) {
            if (inst._zod.parse = base, inst._zod.run === wrapped)
              inst._zod.run = base;
            return base(payload, ctx);
          }
        }
        let input = payload.value;
        if (input === null || typeof input !== "object")
          return base(payload, ctx);
        let state = ctx[STATE];
        if (!state)
          state = { buckets: /* @__PURE__ */ new Map, backEdges: void 0 }, ctx[STATE] = state;
        let bucket;
        if (lastCtx === ctx)
          bucket = lastBucket;
        else
          bucket = bucketFor(state, inst), lastCtx = ctx, lastBucket = bucket;
        let hit = bucket.get(input);
        if (hit) {
          if (payload.value = hit.value, hit.issues) {
            if (hit.issues.length)
              payload.issues.push(...cloneIssues(hit.issues));
          } else
            payload.memo = !0, state.backEdges ?? (state.backEdges = /* @__PURE__ */ new Set), state.backEdges.add(hit.value);
          return payload;
        }
        handoff = bucket;
        let depth = open.length, result = base(payload, ctx);
        handoff = void 0;
        let entry = open.length > depth ? open.pop() : void 0;
        if (result instanceof Promise)
          return result.then((r) => {
            if (entry)
              entry.issues = r.issues.length ? cloneIssues(r.issues) : NO_ISSUES;
            return r;
          });
        if (entry)
          entry.issues = result.issues.length ? cloneIssues(result.issues) : NO_ISSUES;
        return result;
      };
      if (inst._zod.parse = wrapped, inst._zod.run === base)
        inst._zod.run = wrapped;
    });
  }
};
function memoizer() {
  return memo;
}
function isBackEdge(ctx, value) {
  let backEdges = ctx[STATE]?.backEdges;
  return backEdges !== void 0 && value !== null && typeof value === "object" && backEdges.has(value);
}
// node_modules/zod/v4/locales/en.js
var error = () => {
  let Sizable = {
    string: { unit: "characters", verb: "to have" },
    file: { unit: "bytes", verb: "to have" },
    array: { unit: "items", verb: "to have" },
    set: { unit: "items", verb: "to have" },
    map: { unit: "entries", verb: "to have" }
  };
  function getSizing(origin) {
    return Sizable[origin] ?? null;
  }
  let FormatDictionary = {
    regex: "input",
    email: "email address",
    url: "URL",
    emoji: "emoji",
    uuid: "UUID",
    uuidv4: "UUIDv4",
    uuidv6: "UUIDv6",
    nanoid: "nanoid",
    guid: "GUID",
    cuid: "cuid",
    cuid2: "cuid2",
    ulid: "ULID",
    xid: "XID",
    ksuid: "KSUID",
    datetime: "ISO datetime",
    date: "ISO date",
    time: "ISO time",
    duration: "ISO duration",
    ipv4: "IPv4 address",
    ipv6: "IPv6 address",
    mac: "MAC address",
    cidrv4: "IPv4 range",
    cidrv6: "IPv6 range",
    base64: "base64-encoded string",
    base64url: "base64url-encoded string",
    json_string: "JSON string",
    e164: "E.164 number",
    credit_card: "credit card number",
    jwt: "JWT",
    template_literal: "input"
  }, TypeDictionary = {
    nan: "NaN"
  };
  function getTypeName(type, input) {
    if (type === "number" && typeof input === "number" && !Number.isFinite(input))
      return String(input);
    return TypeDictionary[type] ?? type;
  }
  return (issue) => {
    switch (issue.code) {
      case "invalid_type": {
        let expected = getTypeName(issue.expected), receivedType = parsedType(issue.input), received = getTypeName(receivedType, issue.input);
        return `Invalid input: expected ${expected}, received ${received}`;
      }
      case "invalid_value":
        if (issue.values.length === 1)
          return `Invalid input: expected ${stringifyPrimitive(issue.values[0])}`;
        return `Invalid option: expected one of ${joinValues(issue.values, "|")}`;
      case "too_big": {
        let adj = issue.exact ? "exactly " : issue.inclusive ? "<=" : "<", sizing = getSizing(issue.origin);
        if (sizing)
          return `Too big: expected ${issue.origin ?? "value"} to have ${adj}${issue.maximum.toString()} ${sizing.unit ?? "elements"}`;
        return `Too big: expected ${issue.origin ?? "value"} to be ${adj}${issue.maximum.toString()}`;
      }
      case "too_small": {
        let adj = issue.exact ? "exactly " : issue.inclusive ? ">=" : ">", sizing = getSizing(issue.origin);
        if (sizing)
          return `Too small: expected ${issue.origin} to have ${adj}${issue.minimum.toString()} ${sizing.unit}`;
        return `Too small: expected ${issue.origin} to be ${adj}${issue.minimum.toString()}`;
      }
      case "invalid_format": {
        let _issue = issue;
        if (_issue.format === "starts_with")
          return `Invalid string: must start with "${_issue.prefix}"`;
        if (_issue.format === "ends_with")
          return `Invalid string: must end with "${_issue.suffix}"`;
        if (_issue.format === "includes")
          return `Invalid string: must include "${_issue.includes}"`;
        if (_issue.format === "regex")
          return `Invalid string: must match pattern ${_issue.pattern}`;
        return `Invalid ${FormatDictionary[_issue.format] ?? issue.format}`;
      }
      case "not_multiple_of":
        return `Invalid number: must be a multiple of ${issue.divisor}`;
      case "unrecognized_keys":
        return `Unrecognized key${issue.keys.length > 1 ? "s" : ""}: ${joinValues(issue.keys, ", ")}`;
      case "invalid_key":
        return `Invalid key in ${issue.origin}`;
      case "invalid_union":
        if (issue.options && Array.isArray(issue.options) && issue.options.length > 0)
          return `Invalid discriminator value. Expected ${issue.options.map((o) => `'${o}'`).join(" | ")}`;
        if (issue.inclusive === !1)
          return "Invalid input: more than one option matched";
        return "Invalid input";
      case "invalid_element":
        return `Invalid value in ${issue.origin}`;
      default:
        return "Invalid input";
    }
  };
};
function en_default() {
  return {
    localeError: error()
  };
}
// node_modules/zod/v4/core/registries.js
var _a2;
class $ZodRegistry {
  constructor() {
    this._map = /* @__PURE__ */ new WeakMap, this._idmap = /* @__PURE__ */ new Map;
  }
  add(schema, ..._meta) {
    let meta = _meta[0];
    if (this._map.set(schema, meta), meta && typeof meta === "object" && "id" in meta)
      this._idmap.set(meta.id, schema);
    return this;
  }
  clear() {
    return this._map = /* @__PURE__ */ new WeakMap, this._idmap = /* @__PURE__ */ new Map, this;
  }
  remove(schema) {
    let meta = this._map.get(schema);
    if (meta && typeof meta === "object" && "id" in meta)
      this._idmap.delete(meta.id);
    return this._map.delete(schema), this;
  }
  get(schema) {
    let p = schema._zod.parent;
    if (p) {
      let pm = { ...this.get(p) ?? {} };
      delete pm.id;
      let f = { ...pm, ...this._map.get(schema) };
      return Object.keys(f).length ? f : void 0;
    }
    return this._map.get(schema);
  }
  has(schema) {
    return this._map.has(schema);
  }
}
function registry() {
  return new $ZodRegistry;
}
(_a2 = globalThis).__zod_globalRegistry ?? (_a2.__zod_globalRegistry = registry());
var globalRegistry = globalThis.__zod_globalRegistry;
// node_modules/zod/v4/core/api.js
function _string(Class, params) {
  return new Class({
    type: "string",
    ...normalizeParams(params)
  });
}
function _email(Class, params) {
  return new Class({
    type: "string",
    format: "email",
    check: "string_format",
    abort: !1,
    ...normalizeParams(params)
  });
}
function _guid(Class, params) {
  return new Class({
    type: "string",
    format: "guid",
    check: "string_format",
    abort: !1,
    ...normalizeParams(params)
  });
}
function _uuid(Class, params) {
  return new Class({
    type: "string",
    format: "uuid",
    check: "string_format",
    abort: !1,
    ...normalizeParams(params)
  });
}
function _uuidv4(Class, params) {
  return new Class({
    type: "string",
    format: "uuid",
    check: "string_format",
    abort: !1,
    version: "v4",
    ...normalizeParams(params)
  });
}
function _uuidv6(Class, params) {
  return new Class({
    type: "string",
    format: "uuid",
    check: "string_format",
    abort: !1,
    version: "v6",
    ...normalizeParams(params)
  });
}
function _uuidv7(Class, params) {
  return new Class({
    type: "string",
    format: "uuid",
    check: "string_format",
    abort: !1,
    version: "v7",
    ...normalizeParams(params)
  });
}
function _url(Class, params) {
  return new Class({
    type: "string",
    format: "url",
    check: "string_format",
    abort: !1,
    ...normalizeParams(params)
  });
}
function _emoji2(Class, params) {
  return new Class({
    type: "string",
    format: "emoji",
    check: "string_format",
    abort: !1,
    ...normalizeParams(params)
  });
}
function _nanoid(Class, params) {
  return new Class({
    type: "string",
    format: "nanoid",
    check: "string_format",
    abort: !1,
    ...normalizeParams(params)
  });
}
function _cuid(Class, params) {
  return new Class({
    type: "string",
    format: "cuid",
    check: "string_format",
    abort: !1,
    ...normalizeParams(params)
  });
}
function _cuid2(Class, params) {
  return new Class({
    type: "string",
    format: "cuid2",
    check: "string_format",
    abort: !1,
    ...normalizeParams(params)
  });
}
function _ulid(Class, params) {
  return new Class({
    type: "string",
    format: "ulid",
    check: "string_format",
    abort: !1,
    ...normalizeParams(params)
  });
}
function _xid(Class, params) {
  return new Class({
    type: "string",
    format: "xid",
    check: "string_format",
    abort: !1,
    ...normalizeParams(params)
  });
}
function _ksuid(Class, params) {
  return new Class({
    type: "string",
    format: "ksuid",
    check: "string_format",
    abort: !1,
    ...normalizeParams(params)
  });
}
function _ipv4(Class, params) {
  return new Class({
    type: "string",
    format: "ipv4",
    check: "string_format",
    abort: !1,
    ...normalizeParams(params)
  });
}
function _ipv6(Class, params) {
  return new Class({
    type: "string",
    format: "ipv6",
    check: "string_format",
    abort: !1,
    ...normalizeParams(params)
  });
}
function _cidrv4(Class, params) {
  return new Class({
    type: "string",
    format: "cidrv4",
    check: "string_format",
    abort: !1,
    ...normalizeParams(params)
  });
}
function _cidrv6(Class, params) {
  return new Class({
    type: "string",
    format: "cidrv6",
    check: "string_format",
    abort: !1,
    ...normalizeParams(params)
  });
}
function _base64(Class, params) {
  return new Class({
    type: "string",
    format: "base64",
    check: "string_format",
    abort: !1,
    ...normalizeParams(params)
  });
}
function _base64url(Class, params) {
  return new Class({
    type: "string",
    format: "base64url",
    check: "string_format",
    abort: !1,
    ...normalizeParams(params)
  });
}
function _e164(Class, params) {
  return new Class({
    type: "string",
    format: "e164",
    check: "string_format",
    abort: !1,
    ...normalizeParams(params)
  });
}
function _jwt(Class, params) {
  return new Class({
    type: "string",
    format: "jwt",
    check: "string_format",
    abort: !1,
    ...normalizeParams(params)
  });
}
function _isoDateTime(Class, params) {
  return new Class({
    type: "string",
    format: "datetime",
    check: "string_format",
    offset: !1,
    local: !1,
    precision: null,
    ...normalizeParams(params)
  });
}
function _isoDate(Class, params) {
  return new Class({
    type: "string",
    format: "date",
    check: "string_format",
    ...normalizeParams(params)
  });
}
function _isoTime(Class, params) {
  return new Class({
    type: "string",
    format: "time",
    check: "string_format",
    precision: null,
    ...normalizeParams(params)
  });
}
function _isoDuration(Class, params) {
  return new Class({
    type: "string",
    format: "duration",
    check: "string_format",
    ...normalizeParams(params)
  });
}
function _number(Class, params) {
  return new Class({
    type: "number",
    checks: [],
    ...normalizeParams(params)
  });
}
function _coercedNumber(Class, params) {
  return new Class({
    type: "number",
    coerce: !0,
    checks: [],
    ...normalizeParams(params)
  });
}
function _int(Class, params) {
  return new Class({
    type: "number",
    check: "number_format",
    abort: !1,
    format: "safeint",
    ...normalizeParams(params)
  });
}
function _boolean(Class, params) {
  return new Class({
    type: "boolean",
    ...normalizeParams(params)
  });
}
function _null2(Class, params) {
  return new Class({
    type: "null",
    ...normalizeParams(params)
  });
}
function _any(Class) {
  return new Class({
    type: "any"
  });
}
function _unknown(Class) {
  return new Class({
    type: "unknown"
  });
}
function _never(Class, params) {
  return new Class({
    type: "never",
    ...normalizeParams(params)
  });
}
function _lt(value, params) {
  return new $ZodCheckLessThan({
    check: "less_than",
    ...normalizeParams(params),
    value,
    inclusive: !1
  });
}
function _lte(value, params) {
  return new $ZodCheckLessThan({
    check: "less_than",
    ...normalizeParams(params),
    value,
    inclusive: !0
  });
}
function _gt(value, params) {
  return new $ZodCheckGreaterThan({
    check: "greater_than",
    ...normalizeParams(params),
    value,
    inclusive: !1
  });
}
function _gte(value, params) {
  return new $ZodCheckGreaterThan({
    check: "greater_than",
    ...normalizeParams(params),
    value,
    inclusive: !0
  });
}
function _multipleOf(value, params) {
  return new $ZodCheckMultipleOf({
    check: "multiple_of",
    ...normalizeParams(params),
    value
  });
}
function _maxLength(maximum, params) {
  return new $ZodCheckMaxLength({
    check: "max_length",
    ...normalizeParams(params),
    maximum
  });
}
function _minLength(minimum, params) {
  return new $ZodCheckMinLength({
    check: "min_length",
    ...normalizeParams(params),
    minimum
  });
}
function _length(length, params) {
  return new $ZodCheckLengthEquals({
    check: "length_equals",
    ...normalizeParams(params),
    length
  });
}
function _regex(pattern, params) {
  return new $ZodCheckRegex({
    check: "string_format",
    format: "regex",
    ...normalizeParams(params),
    pattern
  });
}
function _lowercase(params) {
  return new $ZodCheckLowerCase({
    check: "string_format",
    format: "lowercase",
    ...normalizeParams(params)
  });
}
function _uppercase(params) {
  return new $ZodCheckUpperCase({
    check: "string_format",
    format: "uppercase",
    ...normalizeParams(params)
  });
}
function _includes(includes, params) {
  return new $ZodCheckIncludes({
    check: "string_format",
    format: "includes",
    ...normalizeParams(params),
    includes
  });
}
function _startsWith(prefix, params) {
  return new $ZodCheckStartsWith({
    check: "string_format",
    format: "starts_with",
    ...normalizeParams(params),
    prefix
  });
}
function _endsWith(suffix, params) {
  return new $ZodCheckEndsWith({
    check: "string_format",
    format: "ends_with",
    ...normalizeParams(params),
    suffix
  });
}
function _overwrite(tx) {
  return new $ZodCheckOverwrite({
    check: "overwrite",
    tx
  });
}
function _normalize(form) {
  return _overwrite((input) => input.normalize(form));
}
function _trim() {
  return _overwrite((input) => input.trim());
}
function _toLowerCase() {
  return _overwrite((input) => input.toLowerCase());
}
function _toUpperCase() {
  return _overwrite((input) => input.toUpperCase());
}
function _slugify() {
  return _overwrite((input) => slugify(input));
}
function _array(Class, element, params) {
  return new Class({
    type: "array",
    element,
    ...normalizeParams(params)
  });
}
function _custom(Class, fn, _params) {
  let norm = normalizeParams(_params);
  return norm.abort ?? (norm.abort = !0), new Class({
    type: "custom",
    check: "custom",
    fn,
    ...norm
  });
}
function _refine(Class, fn, _params) {
  return new Class({
    type: "custom",
    check: "custom",
    fn,
    ...normalizeParams(_params)
  });
}
function _superRefine(fn, params) {
  let ch = _check((payload) => (payload.addIssue = (issue2) => {
    if (typeof issue2 === "string")
      payload.issues.push(issue(issue2, payload.value, ch._zod.def));
    else {
      let _issue = issue2;
      if (_issue.fatal)
        _issue.continue = !1;
      if (_issue.code ?? (_issue.code = "custom"), !("input" in _issue))
        _issue.input = payload.value;
      _issue.inst ?? (_issue.inst = ch), _issue.continue ?? (_issue.continue = !ch._zod.def.abort), payload.issues.push(issue(_issue));
    }
  }, fn(payload.value, payload)), params);
  return ch;
}
function _check(fn, params) {
  let ch = new $ZodCheck({
    check: "custom",
    ...normalizeParams(params)
  });
  return ch._zod.check = fn, ch;
}
// node_modules/zod/v4/core/to-json-schema.js
function assignProps(target, ...sources) {
  for (let source of sources)
    for (let key of Reflect.ownKeys(source))
      if (Object.prototype.propertyIsEnumerable.call(source, key))
        assignProp(target, key, source[key]);
  return target;
}
function initializeContext(params) {
  let target = params?.target ?? "draft-2020-12";
  if (target === "draft-4")
    target = "draft-04";
  if (target === "draft-7")
    target = "draft-07";
  return {
    processors: params.processors ?? {},
    metadataRegistry: params?.metadata ?? globalRegistry,
    target,
    unrepresentable: params?.unrepresentable ?? "throw",
    override: params?.override ?? (() => {}),
    io: params?.io ?? "output",
    counter: 0,
    seen: /* @__PURE__ */ new Map,
    sharedDefsExtractedFor: void 0,
    sharedEmitDoneFor: void 0,
    cycles: params?.cycles ?? "ref",
    reused: params?.reused ?? "inline",
    intersections: [],
    deferred: [],
    external: params?.external ?? void 0
  };
}
function handleUnrepresentable(schema, ctx, json, params, message) {
  let result = typeof ctx.unrepresentable === "function" ? ctx.unrepresentable({ zodSchema: schema, path: params.path, message }) : ctx.unrepresentable;
  if (result === "any")
    return !1;
  if (result === void 0 || result === "throw")
    throw Error(message);
  return Object.assign(json, result), !0;
}
function process2(schema, ctx, _params = { path: [], schemaPath: [] }) {
  var _a;
  let def = schema._zod.def, seen = ctx.seen.get(schema);
  if (seen) {
    if (seen.count++, _params.schemaPath.includes(schema))
      seen.cycle = _params.path;
    return seen.schema;
  }
  let result = { schema: {}, count: 1, cycle: void 0, path: _params.path };
  ctx.seen.set(schema, result), ctx.sharedDefsExtractedFor = void 0, ctx.sharedEmitDoneFor = void 0;
  let overrideSchema = schema._zod.toJSONSchema?.();
  if (overrideSchema)
    result.schema = overrideSchema;
  else {
    let params = {
      ..._params,
      schemaPath: [..._params.schemaPath, schema],
      path: _params.path
    };
    if (schema._zod.processJSONSchema)
      schema._zod.processJSONSchema(ctx, result.schema, params);
    else {
      let _json = result.schema, processor = ctx.processors[def.type];
      if (!processor)
        throw Error(`[toJSONSchema]: Non-representable type encountered: ${def.type}`);
      processor(schema, ctx, _json, params);
    }
    let parent = schema._zod.parent;
    if (parent) {
      if (!result.ref)
        result.ref = parent;
      process2(parent, ctx, params), ctx.seen.get(parent).isParent = !0;
    }
  }
  let meta = ctx.metadataRegistry.get(schema);
  if (meta)
    assignProps(result.schema, meta);
  if (ctx.io === "input" && isTransforming(schema))
    delete result.schema.examples, delete result.schema.default;
  if (ctx.io === "input" && "_prefault" in result.schema)
    (_a = result.schema).default ?? (_a.default = result.schema._prefault);
  return delete result.schema._prefault, ctx.seen.get(schema).schema;
}
function encodeJSONPointerSegment(segment) {
  return segment.replace(/~/g, "~0").replace(/\//g, "~1");
}
function extractDefs(ctx, schema) {
  let root = ctx.seen.get(schema);
  if (!root)
    throw Error("Unprocessed schema. This is a bug in Zod.");
  if (ctx.external && ctx.sharedDefsExtractedFor === ctx.external)
    return;
  let idToSchema = /* @__PURE__ */ new Map;
  for (let entry of ctx.seen.entries()) {
    let id = ctx.metadataRegistry.get(entry[0])?.id;
    if (id) {
      let existing = idToSchema.get(id);
      if (existing && existing !== entry[0])
        throw Error(`Duplicate schema id "${id}" detected during JSON Schema conversion. Two different schemas cannot share the same id when converted together.`);
      idToSchema.set(id, entry[0]);
    }
  }
  let makeURI = (entry) => {
    let defsSegment = ctx.target === "draft-2020-12" ? "$defs" : "definitions";
    if (ctx.external) {
      let externalId = ctx.external.registry.get(entry[0])?.id, uriGenerator = ctx.external.uri ?? ((id) => id);
      if (externalId)
        return { ref: uriGenerator(externalId) };
      let id = entry[1].defId ?? entry[1].schema.id ?? `schema${ctx.counter++}`;
      return entry[1].defId = id, { defId: id, ref: `${uriGenerator("__shared")}#/${defsSegment}/${encodeJSONPointerSegment(id)}` };
    }
    let uriPrefix = "#", defUriPrefix = `${uriPrefix}/${defsSegment}/`;
    if (entry[1] === root && !entry[1].schema.id)
      return { ref: uriPrefix };
    let defId = entry[1].schema.id ?? `__schema${ctx.counter++}`;
    return { defId, ref: defUriPrefix + encodeJSONPointerSegment(defId) };
  }, extractToDef = (entry) => {
    if (entry[1].schema.$ref)
      return;
    let seen = entry[1], { ref, defId } = makeURI(entry);
    if (seen.def = { ...seen.schema }, defId)
      seen.defId = defId;
    let schema = seen.schema;
    for (let key in schema)
      delete schema[key];
    schema.$ref = ref;
  };
  if (ctx.cycles === "throw")
    for (let entry of ctx.seen.entries()) {
      let seen = entry[1];
      if (seen.cycle)
        throw Error(`Cycle detected: #/${seen.cycle?.join("/")}/<root>

Set the \`cycles\` parameter to \`"ref"\` to resolve cyclical schemas with defs.`);
    }
  for (let entry of ctx.seen.entries()) {
    let seen = entry[1];
    if (schema === entry[0]) {
      extractToDef(entry);
      continue;
    }
    if (ctx.external) {
      let ext = ctx.external.registry.get(entry[0])?.id;
      if (schema !== entry[0] && ext) {
        extractToDef(entry);
        continue;
      }
    }
    if (ctx.metadataRegistry.get(entry[0])?.id) {
      extractToDef(entry);
      continue;
    }
    if (seen.cycle) {
      extractToDef(entry);
      continue;
    }
    if (seen.count > 1) {
      if (ctx.reused === "ref") {
        extractToDef(entry);
        continue;
      }
    }
  }
  if (ctx.external)
    ctx.sharedDefsExtractedFor = ctx.external;
}
function compactTypeUnion(schema) {
  let options = schema.anyOf;
  if (!Array.isArray(options) || options.length === 0 || schema.type !== void 0)
    return;
  let types = [];
  for (let option of options) {
    if (!option || typeof option !== "object")
      return;
    compactTypeUnion(option);
    let keys = Object.keys(option);
    if (keys.length !== 1 || keys[0] !== "type")
      return;
    let type = option.type;
    for (let member of Array.isArray(type) ? type : [type]) {
      if (typeof member !== "string")
        return;
      if (!types.includes(member))
        types.push(member);
    }
  }
  delete schema.anyOf, schema.type = types.length === 1 ? types[0] : types;
}
var FOLDABLE_KEYS = /* @__PURE__ */ new Set(["type", "properties", "required", "additionalProperties"]), UNION_KEYS = ["oneOf", "anyOf"];
function undeclaredConstraint(member) {
  let extra = member.additionalProperties;
  if (extra === void 0 || extra === !1 || typeof extra !== "object" || extra === null)
    return null;
  return Object.keys(extra).length ? extra : null;
}
function foldObjects(members) {
  let objects = [];
  for (let member of members) {
    if (typeof member !== "object" || member.type !== "object")
      return null;
    for (let key in member)
      if (!FOLDABLE_KEYS.has(key))
        return null;
    objects.push(member);
  }
  let properties = {}, required = /* @__PURE__ */ new Set;
  for (let object of objects) {
    for (let key in object.properties) {
      if (Object.prototype.hasOwnProperty.call(properties, key))
        continue;
      let parts = [];
      for (let other of objects) {
        let part = other.properties?.[key] ?? undeclaredConstraint(other);
        if (part === null || part === void 0)
          continue;
        if (!parts.some((seen) => JSON.stringify(seen) === JSON.stringify(part)))
          parts.push(part);
      }
      let merged = parts.length === 1 ? parts[0] : foldObjects(parts) ?? { allOf: parts };
      assignProp(properties, key, merged);
    }
    for (let key of object.required ?? [])
      required.add(key);
  }
  let folded = { type: "object", properties };
  if (required.size)
    folded.required = [...required];
  if (objects.every((object) => object.additionalProperties === !1))
    folded.additionalProperties = !1;
  else {
    let constraints = [];
    for (let object of objects) {
      let constraint = undeclaredConstraint(object);
      if (constraint && !constraints.some((seen) => JSON.stringify(seen) === JSON.stringify(constraint)))
        constraints.push(constraint);
    }
    if (constraints.length === 1)
      folded.additionalProperties = constraints[0];
    else if (constraints.length > 1)
      folded.additionalProperties = { allOf: constraints };
  }
  return folded;
}
function foldIntersection(json) {
  let allOf = json.allOf;
  if (!Array.isArray(allOf) || allOf.length < 2)
    return;
  for (let key of FOLDABLE_KEYS)
    if (key in json)
      return;
  let unions = allOf.filter((m) => UNION_KEYS.some((k) => Array.isArray(m[k]))), folded = null;
  if (!unions.length)
    folded = foldObjects(allOf);
  else {
    let union = unions[0], keyword = UNION_KEYS.find((k) => Array.isArray(union[k]));
    if (Object.keys(union).length !== 1)
      return;
    let rest = allOf.filter((m) => m !== union), branches = union[keyword].map((branch) => foldObjects([...rest, branch]));
    if (branches.some((b) => !b))
      return;
    folded = { [keyword]: branches };
  }
  if (!folded)
    return;
  delete json.allOf, assignProps(json, folded);
}
function finalize(ctx, schema) {
  let root = ctx.seen.get(schema);
  if (!root)
    throw Error("Unprocessed schema. This is a bug in Zod.");
  let flattenRef = (zodSchema) => {
    let seen = ctx.seen.get(zodSchema);
    if (seen.ref === null)
      return;
    let schema = seen.def ?? seen.schema, _cached = { ...schema }, ref = seen.ref;
    if (seen.ref = null, ref) {
      flattenRef(ref);
      let refSeen = ctx.seen.get(ref), refSchema = refSeen.schema;
      if (refSchema.$ref && (ctx.target === "draft-07" || ctx.target === "draft-04" || ctx.target === "openapi-3.0"))
        schema.allOf = schema.allOf ?? [], schema.allOf.push(refSchema);
      else
        assignProps(schema, refSchema);
      if (assignProps(schema, _cached), zodSchema._zod.parent === ref)
        for (let key in schema) {
          if (key === "$ref" || key === "allOf")
            continue;
          if (!(key in _cached))
            delete schema[key];
        }
      if (refSchema.$ref && refSeen.def)
        for (let key in schema) {
          if (key === "$ref" || key === "allOf")
            continue;
          if (key in refSeen.def && JSON.stringify(schema[key]) === JSON.stringify(refSeen.def[key]))
            delete schema[key];
        }
    }
    let parent = zodSchema._zod.parent;
    if (parent && parent !== ref) {
      flattenRef(parent);
      let parentSeen = ctx.seen.get(parent);
      if (parentSeen?.schema.$ref) {
        if (schema.$ref = parentSeen.schema.$ref, parentSeen.def)
          for (let key in schema) {
            if (key === "$ref" || key === "allOf")
              continue;
            if (key in parentSeen.def && JSON.stringify(schema[key]) === JSON.stringify(parentSeen.def[key]))
              delete schema[key];
          }
      }
    }
    ctx.override({
      zodSchema,
      jsonSchema: schema,
      path: seen.path ?? []
    });
  };
  if (!ctx.external || ctx.sharedEmitDoneFor !== ctx.external) {
    for (let entry of [...ctx.seen.entries()].reverse())
      flattenRef(entry[0]);
    if (ctx.target !== "openapi-3.0")
      for (let entry of ctx.seen.entries())
        compactTypeUnion(entry[1].def ?? entry[1].schema);
    for (let rewrite of ctx.deferred)
      rewrite();
    if (ctx.intersections.length) {
      let carriers = /* @__PURE__ */ new Map;
      for (let seen of ctx.seen.values())
        for (let json of [seen.schema, seen.def]) {
          let allOf = json?.allOf;
          if (!Array.isArray(allOf))
            continue;
          let existing = carriers.get(allOf);
          if (existing)
            existing.push(json);
          else
            carriers.set(allOf, [json]);
        }
      for (let allOf of ctx.intersections)
        for (let json of carriers.get(allOf) ?? [])
          foldIntersection(json);
    }
  }
  let result = {};
  if (ctx.target === "draft-2020-12")
    result.$schema = "https://json-schema.org/draft/2020-12/schema";
  else if (ctx.target === "draft-07")
    result.$schema = "http://json-schema.org/draft-07/schema#";
  else if (ctx.target === "draft-04")
    result.$schema = "http://json-schema.org/draft-04/schema#";
  else if (ctx.target === "openapi-3.0")
    ;
  if (ctx.external?.uri) {
    let id = ctx.external.registry.get(schema)?.id;
    if (!id)
      throw Error("Schema is missing an `id` property");
    result.$id = ctx.external.uri(id);
  }
  assignProps(result, root.defId ? root.schema : root.def ?? root.schema);
  let rootMetaId = ctx.metadataRegistry.get(schema)?.id;
  if (rootMetaId !== void 0 && result.id === rootMetaId)
    delete result.id;
  let defs = ctx.external?.defs ?? {};
  if (!ctx.external || ctx.sharedEmitDoneFor !== ctx.external)
    for (let entry of ctx.seen.entries()) {
      let seen = entry[1];
      if (seen.def && seen.defId) {
        if (seen.def.id === seen.defId)
          delete seen.def.id;
        assignProp(defs, seen.defId, seen.def);
      }
    }
  if (ctx.external)
    ctx.sharedEmitDoneFor = ctx.external;
  if (ctx.external)
    ;
  else if (Object.keys(defs).length > 0)
    if (ctx.target === "draft-2020-12")
      result.$defs = defs;
    else
      result.definitions = defs;
  try {
    let finalized = JSON.parse(JSON.stringify(result));
    return Object.defineProperty(finalized, "~standard", {
      value: {
        ...schema["~standard"],
        jsonSchema: {
          input: createStandardJSONSchemaMethod(schema, "input", ctx.processors),
          output: createStandardJSONSchemaMethod(schema, "output", ctx.processors)
        }
      },
      enumerable: !1,
      writable: !1
    }), finalized;
  } catch (_err) {
    throw Error("Error converting schema to JSON.");
  }
}
function isTransforming(_schema, _ctx) {
  let ctx = _ctx ?? { seen: /* @__PURE__ */ new Set };
  if (ctx.seen.has(_schema))
    return !1;
  ctx.seen.add(_schema);
  let def = _schema._zod.def;
  if (def.type === "transform")
    return !0;
  if (def.type === "array")
    return isTransforming(def.element, ctx);
  if (def.type === "set")
    return isTransforming(def.valueType, ctx);
  if (def.type === "lazy")
    return isTransforming(def.getter(), ctx);
  if (def.type === "promise" || def.type === "optional" || def.type === "nonoptional" || def.type === "nullable" || def.type === "readonly" || def.type === "default" || def.type === "prefault" || def.type === "catch")
    return isTransforming(def.innerType, ctx);
  if (def.type === "intersection")
    return isTransforming(def.left, ctx) || isTransforming(def.right, ctx);
  if (def.type === "record" || def.type === "map")
    return isTransforming(def.keyType, ctx) || isTransforming(def.valueType, ctx);
  if (def.type === "pipe") {
    if (_schema._zod.traits.has("$ZodCodec"))
      return !0;
    return isTransforming(def.in, ctx) || isTransforming(def.out, ctx);
  }
  if (def.type === "object") {
    for (let key in def.shape)
      if (isTransforming(def.shape[key], ctx))
        return !0;
    return !1;
  }
  if (def.type === "union") {
    for (let option of def.options)
      if (isTransforming(option, ctx))
        return !0;
    return !1;
  }
  if (def.type === "tuple") {
    for (let item of def.items)
      if (isTransforming(item, ctx))
        return !0;
    if (def.rest && isTransforming(def.rest, ctx))
      return !0;
    return !1;
  }
  return !1;
}
var createToJSONSchemaMethod = (schema, processors = {}) => (params) => {
  let ctx = initializeContext({ ...params, processors });
  return process2(schema, ctx), extractDefs(ctx, schema), finalize(ctx, schema);
}, createStandardJSONSchemaMethod = (schema, io, processors = {}) => (params) => {
  let { libraryOptions, target } = params ?? {}, ctx = initializeContext({ ...libraryOptions ?? {}, target, io, processors });
  return process2(schema, ctx), extractDefs(ctx, schema), finalize(ctx, schema);
};
// node_modules/zod/v4/core/json-schema-processors.js
var formatMap = {
  guid: "uuid",
  url: "uri",
  datetime: "date-time",
  json_string: "json-string",
  regex: ""
}, stringProcessor = (schema, ctx, _json, _params) => {
  let json = _json;
  json.type = "string";
  let { minimum, maximum, format, patterns, contentEncoding, laxFormat } = schema._zod.bag;
  if (typeof minimum === "number")
    json.minLength = minimum;
  if (typeof maximum === "number")
    json.maxLength = maximum;
  if (format) {
    if (json.format = formatMap[format] ?? format, json.format === "")
      delete json.format;
    if (format === "time" || laxFormat)
      delete json.format;
  }
  if (contentEncoding)
    json.contentEncoding = contentEncoding;
  if (patterns && patterns.size > 0) {
    let patternList = [...patterns];
    if (patternList.length === 1)
      json.pattern = patternList[0].source;
    else if (patternList.length > 1)
      json.allOf = [
        ...patternList.map((regex) => ({
          ...ctx.target === "draft-07" || ctx.target === "draft-04" || ctx.target === "openapi-3.0" ? { type: "string" } : {},
          pattern: regex.source
        }))
      ];
  }
}, numberProcessor = (schema, ctx, _json, params) => {
  let json = _json, { minimum, maximum, format, multipleOf, exclusiveMaximum, exclusiveMinimum } = schema._zod.bag;
  if (typeof format === "string" && format.includes("int"))
    json.type = "integer";
  else
    json.type = "number";
  let exMin = typeof exclusiveMinimum === "number" && exclusiveMinimum >= (minimum ?? Number.NEGATIVE_INFINITY), exMax = typeof exclusiveMaximum === "number" && exclusiveMaximum <= (maximum ?? Number.POSITIVE_INFINITY), legacy = ctx.target === "draft-04" || ctx.target === "openapi-3.0";
  if (exMin)
    if (legacy)
      json.minimum = exclusiveMinimum, json.exclusiveMinimum = !0;
    else
      json.exclusiveMinimum = exclusiveMinimum;
  else if (typeof minimum === "number")
    json.minimum = minimum;
  if (exMax)
    if (legacy)
      json.maximum = exclusiveMaximum, json.exclusiveMaximum = !0;
    else
      json.exclusiveMaximum = exclusiveMaximum;
  else if (typeof maximum === "number")
    json.maximum = maximum;
  if (typeof multipleOf === "number")
    if (Number.isFinite(multipleOf) && multipleOf !== 0)
      json.multipleOf = Math.abs(multipleOf);
    else
      handleUnrepresentable(schema, ctx, json, params, `A multipleOf divisor of ${multipleOf} cannot be represented in JSON Schema`);
}, booleanProcessor = (_schema, _ctx, json, _params) => {
  json.type = "boolean";
};
var nullProcessor = (_schema, ctx, json, _params) => {
  if (ctx.target === "openapi-3.0")
    json.type = "string", json.nullable = !0, json.enum = [null];
  else
    json.type = "null";
};
var neverProcessor = (_schema, _ctx, json, _params) => {
  json.not = {};
}, anyProcessor = (_schema, _ctx, _json, _params) => {}, unknownProcessor = (_schema, _ctx, _json, _params) => {};
var enumProcessor = (schema, _ctx, json, _params) => {
  let def = schema._zod.def, values = getEnumValues(def.entries);
  if (values.length === 0) {
    json.not = {};
    return;
  }
  if (values.every((v) => typeof v === "number"))
    json.type = "number";
  if (values.every((v) => typeof v === "string"))
    json.type = "string";
  json.enum = values;
}, literalProcessor = (schema, ctx, json, params) => {
  let def = schema._zod.def;
  if (def.values.length === 0) {
    json.not = {};
    return;
  }
  let vals = [];
  for (let val of def.values)
    if (val === void 0) {
      if (handleUnrepresentable(schema, ctx, json, params, "Literal `undefined` cannot be represented in JSON Schema"))
        return;
    } else if (typeof val === "bigint") {
      if (handleUnrepresentable(schema, ctx, json, params, "BigInt literals cannot be represented in JSON Schema"))
        return;
      vals.push(Number(val));
    } else
      vals.push(val);
  if (vals.length === 0)
    ;
  else if (vals.length === 1) {
    let val = vals[0];
    if (json.type = val === null ? "null" : typeof val, ctx.target === "draft-04" || ctx.target === "openapi-3.0")
      json.enum = [val];
    else
      json.const = val;
  } else {
    if (vals.every((v) => typeof v === "number"))
      json.type = "number";
    if (vals.every((v) => typeof v === "string"))
      json.type = "string";
    if (vals.every((v) => typeof v === "boolean"))
      json.type = "boolean";
    if (vals.every((v) => v === null))
      json.type = "null";
    json.enum = vals;
  }
};
var customProcessor = (schema, ctx, json, params) => {
  handleUnrepresentable(schema, ctx, json, params, "Custom types cannot be represented in JSON Schema");
};
var transformProcessor = (schema, ctx, json, params) => {
  handleUnrepresentable(schema, ctx, json, params, "Transforms cannot be represented in JSON Schema");
};
var arrayProcessor = (schema, ctx, _json, params) => {
  let json = _json, def = schema._zod.def, { minimum, maximum } = schema._zod.bag;
  if (typeof minimum === "number")
    json.minItems = minimum;
  if (typeof maximum === "number")
    json.maxItems = maximum;
  json.type = "array", json.items = process2(def.element, ctx, {
    ...params,
    path: [...params.path, "items"]
  });
};
function inputOptin(schema) {
  let def = schema._zod.def;
  if (def.type === "pipe" && def.in._zod.traits.has("$ZodTransform"))
    return inputOptin(def.out);
  if (def.type === "catch")
    return inputOptin(def.innerType);
  return schema._zod.optin;
}
var objectProcessor = (schema, ctx, _json, params) => {
  let json = _json, def = schema._zod.def, shape = def.shape;
  if (Object.getOwnPropertySymbols(shape).length && handleUnrepresentable(schema, ctx, json, params, "Symbol keys cannot be represented in JSON Schema"))
    return;
  json.type = "object", json.properties = {};
  for (let key in shape)
    assignProp(json.properties, key, process2(shape[key], ctx, {
      ...params,
      path: [...params.path, "properties", key]
    }));
  let allKeys = new Set(Object.keys(shape)), requiredKeys = new Set([...allKeys].filter((key) => {
    let field = def.shape[key];
    if (ctx.io === "input")
      return inputOptin(field) === void 0;
    else
      return field._zod.optout === void 0;
  }));
  if (requiredKeys.size > 0)
    json.required = Array.from(requiredKeys);
  if (def.catchall?._zod.def.type === "never")
    json.additionalProperties = !1;
  else if (!def.catchall) {
    if (ctx.io === "output")
      json.additionalProperties = !1;
  } else if (def.catchall)
    json.additionalProperties = process2(def.catchall, ctx, {
      ...params,
      path: [...params.path, "additionalProperties"]
    });
}, unionProcessor = (schema, ctx, json, params) => {
  let def = schema._zod.def, isExclusive = def.inclusive === !1, options = def.options.map((x, i) => process2(x, ctx, {
    ...params,
    path: [...params.path, isExclusive ? "oneOf" : "anyOf", i]
  }));
  if (isExclusive)
    json.oneOf = options;
  else
    json.anyOf = options;
}, intersectionProcessor = (schema, ctx, json, params) => {
  let def = schema._zod.def, a = process2(def.left, ctx, {
    ...params,
    path: [...params.path, "allOf", 0]
  }), b = process2(def.right, ctx, {
    ...params,
    path: [...params.path, "allOf", 1]
  }), isSimpleIntersection = (val) => ("allOf" in val) && Object.keys(val).length === 1, allOf = [
    ...isSimpleIntersection(a) ? a.allOf : [a],
    ...isSimpleIntersection(b) ? b.allOf : [b]
  ];
  json.allOf = allOf, ctx.intersections.push(allOf);
};
function stringifyKeyNames(bySchema, json, visited) {
  if (json.$ref) {
    if (visited.has(json))
      return json;
    visited.add(json);
    let def = bySchema.get(json)?.def;
    if (!def)
      return json;
    let inlined = stringifyKeyNames(bySchema, def, visited);
    return inlined === def ? json : inlined;
  }
  for (let keyword of ["anyOf", "oneOf"]) {
    let branches = json[keyword];
    if (!Array.isArray(branches))
      continue;
    let mapped = branches.map((branch) => stringifyKeyNames(bySchema, branch, visited));
    if (mapped.some((branch, i) => branch !== branches[i]))
      json = { ...json, [keyword]: mapped };
  }
  let types = Array.isArray(json.type) ? json.type : [json.type], numericType = !types.includes("string") && types.some((t) => t === "number" || t === "integer"), values = json.enum ?? (json.const !== void 0 ? [json.const] : void 0);
  if (!numericType && !values?.some((v) => typeof v === "number"))
    return json;
  let { minimum, maximum, exclusiveMinimum, exclusiveMaximum, multipleOf, format, id, ...rest } = json;
  if (rest.enum)
    rest.enum = rest.enum.map((v) => typeof v === "number" ? String(v) : v);
  else if (typeof rest.const === "number")
    rest.const = String(rest.const);
  if (!numericType)
    return rest;
  if (rest.type = "string", !values)
    rest.pattern = (types.includes("number") ? number : integer).source;
  return rest;
}
var pendingRecords = /* @__PURE__ */ new WeakMap;
function rewriteKeyNames(ctx) {
  let bySchema = /* @__PURE__ */ new Map;
  for (let entry of ctx.seen.values())
    if (entry.def && !bySchema.has(entry.schema))
      bySchema.set(entry.schema, entry);
  let rewrites = /* @__PURE__ */ new Map;
  for (let record of pendingRecords.get(ctx) ?? []) {
    let seen = ctx.seen.get(record), names = (seen?.def ?? seen?.schema)?.propertyNames;
    if (!names || names === !0 || rewrites.has(names))
      continue;
    let rewritten = stringifyKeyNames(bySchema, names, /* @__PURE__ */ new Set);
    if (rewritten !== names)
      rewrites.set(names, rewritten);
  }
  if (!rewrites.size)
    return;
  for (let entry of ctx.seen.values())
    for (let carrier of [entry.schema, entry.def]) {
      let rewritten = carrier && rewrites.get(carrier.propertyNames);
      if (rewritten)
        carrier.propertyNames = rewritten;
    }
}
var recordProcessor = (schema, ctx, _json, params) => {
  let json = _json, def = schema._zod.def;
  json.type = "object";
  let keyType = def.keyType, patterns = keyType._zod.bag?.patterns;
  if (def.mode === "loose" && patterns && patterns.size > 0) {
    let valueSchema = process2(def.valueType, ctx, {
      ...params,
      path: [...params.path, "patternProperties", "*"]
    });
    json.patternProperties = {};
    for (let pattern of patterns)
      assignProp(json.patternProperties, pattern.source, valueSchema);
  } else {
    if (ctx.target === "draft-07" || ctx.target === "draft-2020-12") {
      json.propertyNames = process2(def.keyType, ctx, {
        ...params,
        path: [...params.path, "propertyNames"]
      });
      let pending = pendingRecords.get(ctx);
      if (!pending)
        pending = [], pendingRecords.set(ctx, pending), ctx.deferred.push(() => rewriteKeyNames(ctx));
      pending.push(schema);
    }
    json.additionalProperties = process2(def.valueType, ctx, {
      ...params,
      path: [...params.path, "additionalProperties"]
    });
  }
  let keyValues = keyType._zod.values, omittableOnInput = ctx.io === "input" && inputOptin(def.valueType) !== void 0;
  if (keyValues && !def.partial && !omittableOnInput) {
    let validKeyValues = [...keyValues].filter((v) => typeof v === "string" || typeof v === "number");
    if (validKeyValues.length > 0)
      json.required = validKeyValues.map(String);
  }
}, nullableProcessor = (schema, ctx, json, params) => {
  let def = schema._zod.def, inner = process2(def.innerType, ctx, params), seen = ctx.seen.get(schema);
  if (ctx.target === "openapi-3.0")
    seen.ref = def.innerType, json.nullable = !0;
  else
    json.anyOf = [inner, { type: "null" }];
}, nonoptionalProcessor = (schema, ctx, _json, params) => {
  let def = schema._zod.def;
  process2(def.innerType, ctx, params);
  let seen = ctx.seen.get(schema);
  seen.ref = def.innerType;
}, UNREPRESENTABLE_DEFAULT = Symbol();
function serializeDefaultValue(value, schema, ctx, json, params) {
  let unrepresentable = !1, serialized = JSON.stringify(value, (_, val) => {
    if (typeof val !== "bigint")
      return val;
    return unrepresentable = !0, null;
  });
  if (!unrepresentable)
    return JSON.parse(serialized);
  return handleUnrepresentable(schema, ctx, json, params, "BigInt defaults cannot be represented in JSON Schema"), UNREPRESENTABLE_DEFAULT;
}
var defaultProcessor = (schema, ctx, json, params) => {
  let def = schema._zod.def;
  process2(def.innerType, ctx, params);
  let seen = ctx.seen.get(schema);
  seen.ref = def.innerType;
  let value = serializeDefaultValue(def.defaultValue, schema, ctx, json, params);
  if (value !== UNREPRESENTABLE_DEFAULT)
    json.default = value;
}, prefaultProcessor = (schema, ctx, json, params) => {
  let def = schema._zod.def;
  process2(def.innerType, ctx, params);
  let seen = ctx.seen.get(schema);
  if (seen.ref = def.innerType, ctx.io !== "input")
    return;
  let value = serializeDefaultValue(def.defaultValue, schema, ctx, json, params);
  if (value !== UNREPRESENTABLE_DEFAULT)
    json._prefault = value;
}, catchProcessor = (schema, ctx, json, params) => {
  let def = schema._zod.def;
  process2(def.innerType, ctx, params);
  let seen = ctx.seen.get(schema);
  seen.ref = def.innerType;
  let catchValue;
  try {
    catchValue = def.catchValue(void 0);
  } catch {
    handleUnrepresentable(schema, ctx, json, params, "Dynamic catch values are not supported in JSON Schema");
    return;
  }
  json.default = catchValue;
}, pipeProcessor = (schema, ctx, _json, params) => {
  let def = schema._zod.def, inIsTransform = def.in._zod.traits.has("$ZodTransform"), innerType = ctx.io === "input" ? inIsTransform ? def.out : def.in : def.out;
  process2(innerType, ctx, params);
  let seen = ctx.seen.get(schema);
  seen.ref = innerType;
}, readonlyProcessor = (schema, ctx, json, params) => {
  let def = schema._zod.def;
  process2(def.innerType, ctx, params);
  let seen = ctx.seen.get(schema);
  seen.ref = def.innerType, json.readOnly = !0;
};
var optionalProcessor = (schema, ctx, _json, params) => {
  let def = schema._zod.def;
  process2(def.innerType, ctx, params);
  let seen = ctx.seen.get(schema);
  seen.ref = def.innerType;
};
// node_modules/@modelcontextprotocol/sdk/dist/esm/server/zod-compat.js
function isZ4Schema(s) {
  return !!s._zod;
}
function safeParse2(schema, data) {
  if (isZ4Schema(schema))
    return safeParse(schema, data);
  return schema.safeParse(data);
}
function getObjectShape(schema) {
  if (!schema)
    return;
  let rawShape;
  if (isZ4Schema(schema))
    rawShape = schema._zod?.def?.shape;
  else
    rawShape = schema.shape;
  if (!rawShape)
    return;
  if (typeof rawShape === "function")
    try {
      return rawShape();
    } catch {
      return;
    }
  return rawShape;
}
function getLiteralValue(schema) {
  if (isZ4Schema(schema)) {
    let def = schema._zod?.def;
    if (def) {
      if (def.value !== void 0)
        return def.value;
      if (Array.isArray(def.values) && def.values.length > 0)
        return def.values[0];
    }
  }
  let def = schema._def;
  if (def) {
    if (def.value !== void 0)
      return def.value;
    if (Array.isArray(def.values) && def.values.length > 0)
      return def.values[0];
  }
  let directValue = schema.value;
  if (directValue !== void 0)
    return directValue;
  return;
}
// node_modules/zod/v4/classic/errors.js
var _installedErrorProtos = /* @__PURE__ */ new WeakSet([Object.prototype, Error.prototype]);
function _lazyMethod(proto, key, make) {
  Object.defineProperty(proto, key, {
    configurable: !0,
    enumerable: !1,
    get() {
      let value = make(this);
      return Object.defineProperty(this, key, { value, configurable: !0, writable: !0 }), value;
    },
    set(value) {
      Object.defineProperty(this, key, { value, configurable: !0, writable: !0 });
    }
  });
}
var initializer2 = (inst, issues) => {
  $ZodError.init(inst, issues), inst.name = "ZodError";
  let proto = Object.getPrototypeOf(inst);
  if (_installedErrorProtos.has(proto))
    return;
  _installedErrorProtos.add(proto), _lazyMethod(proto, "format", (self) => (mapper) => formatError(self, mapper)), _lazyMethod(proto, "flatten", (self) => (mapper) => flattenError(self, mapper)), _lazyMethod(proto, "addIssue", (self) => (issue) => {
    self.issues.push(issue), self.message = JSON.stringify(self.issues, jsonStringifyReplacer, 2);
  }), _lazyMethod(proto, "addIssues", (self) => (issues) => {
    self.issues.push(...issues), self.message = JSON.stringify(self.issues, jsonStringifyReplacer, 2);
  }), Object.defineProperty(proto, "isEmpty", {
    configurable: !0,
    enumerable: !1,
    get() {
      return this.issues.length === 0;
    }
  });
};
var ZodRealError = /* @__PURE__ */ $constructor("ZodError", initializer2, void 0, {
  Parent: Error
});

// node_modules/zod/v4/classic/parse.js
var parse3 = /* @__PURE__ */ _parse(ZodRealError), parseAsync2 = /* @__PURE__ */ _parseAsync(ZodRealError), safeParse3 = /* @__PURE__ */ _safeParse(ZodRealError), safeParseAsync2 = /* @__PURE__ */ _safeParseAsync(ZodRealError), encode2 = /* @__PURE__ */ _encode(ZodRealError), decode2 = /* @__PURE__ */ _decode(ZodRealError), encodeAsync2 = /* @__PURE__ */ _encodeAsync(ZodRealError), decodeAsync2 = /* @__PURE__ */ _decodeAsync(ZodRealError), safeEncode2 = /* @__PURE__ */ _safeEncode(ZodRealError), safeDecode2 = /* @__PURE__ */ _safeDecode(ZodRealError), safeEncodeAsync2 = /* @__PURE__ */ _safeEncodeAsync(ZodRealError), safeDecodeAsync2 = /* @__PURE__ */ _safeDecodeAsync(ZodRealError);

// node_modules/zod/v4/classic/schemas.js
function _ensureDefaultLocale() {
  if (!globalConfig.localeError)
    config(en_default());
}
function _ensureDefaultMemoizer() {
  if (!globalConfig.memoizer)
    config({ memoizer: memoizer() });
}
var ZodType = /* @__PURE__ */ $constructor("ZodType", (inst, def) => (_ensureDefaultLocale(), $ZodType.init(inst, def), inst.def = def, inst.type = def.type, inst), {
  check(...chks) {
    let def = this.def;
    return this.clone(mergeDefs(def, {
      checks: [
        ...def.checks ?? [],
        ...chks.map((ch) => typeof ch === "function" ? { _zod: { check: ch, def: { check: "custom" }, onattach: [] } } : ch)
      ]
    }), { parent: !0 });
  },
  with(...chks) {
    return this.check(...chks);
  },
  clone(def, params) {
    return clone(this, def, params);
  },
  brand() {
    return this;
  },
  register(reg, meta) {
    return reg.add(this, meta), this;
  },
  refine(check, params) {
    return this.check(refine(check, params));
  },
  superRefine(refinement, params) {
    return this.check(superRefine(refinement, params));
  },
  overwrite(fn) {
    return this.check(_overwrite(fn));
  },
  optional() {
    return optional(this);
  },
  exactOptional() {
    return exactOptional(this);
  },
  nullable() {
    return nullable(this);
  },
  nullish() {
    return optional(nullable(this));
  },
  nonoptional(params) {
    return nonoptional(this, params);
  },
  array() {
    return array(this);
  },
  or(arg) {
    return union([this, arg]);
  },
  and(arg) {
    return intersection(this, arg);
  },
  transform(tx) {
    return pipe(this, transform(tx));
  },
  default(d) {
    return _default(this, d);
  },
  prefault(d) {
    return prefault(this, d);
  },
  catch(params) {
    return _catch(this, params);
  },
  pipe(target) {
    return pipe(this, target);
  },
  readonly() {
    return readonly(this);
  },
  describe(description) {
    let cl = this.clone();
    return globalRegistry.add(cl, { description }), cl;
  },
  meta(...args) {
    if (args.length === 0)
      return globalRegistry.get(this);
    let cl = this.clone();
    return globalRegistry.add(cl, args[0]), cl;
  },
  isOptional() {
    return this.safeParse(void 0).success;
  },
  isNullable() {
    return this.safeParse(null).success;
  },
  apply(fn, ...args) {
    return args.length === 0 ? fn(this) : fn(this, ...args);
  },
  get "~standard"() {
    return hide(this, "~standard", {
      ...standardProps(this),
      jsonSchema: {
        input: createStandardJSONSchemaMethod(this, "input"),
        output: createStandardJSONSchemaMethod(this, "output")
      }
    });
  },
  set "~standard"(value) {
    own(this, "~standard", value);
  },
  parse: function _parse(data, params) {
    return parse3(this, data, params, { callee: _parse });
  },
  parseAsync: async function _parseAsync(data, params) {
    return await parseAsync2(this, data, params, { callee: _parseAsync });
  },
  safeParse(data, params) {
    return safeParse3(this, data, params);
  },
  async safeParseAsync(data, params) {
    return safeParseAsync2(this, data, params);
  },
  get spa() {
    return this?.safeParseAsync;
  },
  set spa(value) {
    own(this, "spa", value);
  },
  encode: function _encode(data, params) {
    return encode2(this, data, params, { callee: _encode });
  },
  decode: function _decode(data, params) {
    return decode2(this, data, params, { callee: _decode });
  },
  encodeAsync: async function _encodeAsync(data, params) {
    return await encodeAsync2(this, data, params, { callee: _encodeAsync });
  },
  decodeAsync: async function _decodeAsync(data, params) {
    return await decodeAsync2(this, data, params, { callee: _decodeAsync });
  },
  safeEncode(data, params) {
    return safeEncode2(this, data, params);
  },
  safeDecode(data, params) {
    return safeDecode2(this, data, params);
  },
  async safeEncodeAsync(data, params) {
    return safeEncodeAsync2(this, data, params);
  },
  async safeDecodeAsync(data, params) {
    return safeDecodeAsync2(this, data, params);
  },
  toJSONSchema(params) {
    return createToJSONSchemaMethod(this, {})(params);
  },
  get description() {
    return globalRegistry.get(this)?.description;
  },
  get _def() {
    return this._zod.def;
  }
}), _ZodString = /* @__PURE__ */ $constructor("_ZodString", (inst, def) => {
  $ZodString.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => stringProcessor(inst, ctx, json, params);
  let bag = inst._zod.bag;
  inst.format = bag.format ?? null, inst.minLength = bag.minimum ?? null, inst.maxLength = bag.maximum ?? null;
}, {
  regex(...args) {
    return this.check(_regex(...args));
  },
  includes(...args) {
    return this.check(_includes(...args));
  },
  startsWith(...args) {
    return this.check(_startsWith(...args));
  },
  endsWith(...args) {
    return this.check(_endsWith(...args));
  },
  min(...args) {
    return this.check(_minLength(...args));
  },
  max(...args) {
    return this.check(_maxLength(...args));
  },
  length(...args) {
    return this.check(_length(...args));
  },
  nonempty(...args) {
    return this.check(_minLength(1, ...args));
  },
  lowercase(params) {
    return this.check(_lowercase(params));
  },
  uppercase(params) {
    return this.check(_uppercase(params));
  },
  trim() {
    return this.check(_trim());
  },
  normalize(...args) {
    return this.check(_normalize(...args));
  },
  toLowerCase() {
    return this.check(_toLowerCase());
  },
  toUpperCase() {
    return this.check(_toUpperCase());
  },
  slugify() {
    return this.check(_slugify());
  }
}), ZodString = /* @__PURE__ */ $constructor("ZodString", (inst, def) => {
  $ZodString.init(inst, def), _ZodString.init(inst, def);
}, {
  email(params) {
    return this.check(_email(ZodEmail, params));
  },
  url(params) {
    return this.check(_url(ZodURL, params));
  },
  jwt(params) {
    return this.check(_jwt(ZodJWT, params));
  },
  emoji(params) {
    return this.check(_emoji2(ZodEmoji, params));
  },
  guid(params) {
    return this.check(_guid(ZodGUID, params));
  },
  uuid(params) {
    return this.check(_uuid(ZodUUID, params));
  },
  uuidv4(params) {
    return this.check(_uuidv4(ZodUUID, params));
  },
  uuidv6(params) {
    return this.check(_uuidv6(ZodUUID, params));
  },
  uuidv7(params) {
    return this.check(_uuidv7(ZodUUID, params));
  },
  nanoid(params) {
    return this.check(_nanoid(ZodNanoID, params));
  },
  cuid(params) {
    return this.check(_cuid(ZodCUID, params));
  },
  cuid2(params) {
    return this.check(_cuid2(ZodCUID2, params));
  },
  ulid(params) {
    return this.check(_ulid(ZodULID, params));
  },
  base64(params) {
    return this.check(_base64(ZodBase64, params));
  },
  base64url(params) {
    return this.check(_base64url(ZodBase64URL, params));
  },
  xid(params) {
    return this.check(_xid(ZodXID, params));
  },
  ksuid(params) {
    return this.check(_ksuid(ZodKSUID, params));
  },
  ipv4(params) {
    return this.check(_ipv4(ZodIPv4, params));
  },
  ipv6(params) {
    return this.check(_ipv6(ZodIPv6, params));
  },
  cidrv4(params) {
    return this.check(_cidrv4(ZodCIDRv4, params));
  },
  cidrv6(params) {
    return this.check(_cidrv6(ZodCIDRv6, params));
  },
  e164(params) {
    return this.check(_e164(ZodE164, params));
  },
  datetime(params) {
    return this.check(_isoDateTime(ZodISODateTime, params));
  },
  date(params) {
    return this.check(_isoDate(ZodISODate, params));
  },
  time(params) {
    return this.check(_isoTime(ZodISOTime, params));
  },
  duration(params) {
    return this.check(_isoDuration(ZodISODuration, params));
  }
});
function string2(params) {
  return _string(ZodString, params);
}
var ZodStringFormat = /* @__PURE__ */ $constructor("ZodStringFormat", (inst, def) => {
  $ZodStringFormat.init(inst, def), _ZodString.init(inst, def);
}), ZodISODateTime = /* @__PURE__ */ $constructor("ZodISODateTime", (inst, def) => {
  $ZodISODateTime.init(inst, def), ZodStringFormat.init(inst, def);
}), ZodISODate = /* @__PURE__ */ $constructor("ZodISODate", (inst, def) => {
  $ZodISODate.init(inst, def), ZodStringFormat.init(inst, def);
}), ZodISOTime = /* @__PURE__ */ $constructor("ZodISOTime", (inst, def) => {
  $ZodISOTime.init(inst, def), ZodStringFormat.init(inst, def);
}), ZodISODuration = /* @__PURE__ */ $constructor("ZodISODuration", (inst, def) => {
  $ZodISODuration.init(inst, def), ZodStringFormat.init(inst, def);
}), ZodEmail = /* @__PURE__ */ $constructor("ZodEmail", (inst, def) => {
  $ZodEmail.init(inst, def), ZodStringFormat.init(inst, def);
});
var ZodGUID = /* @__PURE__ */ $constructor("ZodGUID", (inst, def) => {
  $ZodGUID.init(inst, def), ZodStringFormat.init(inst, def);
});
var ZodUUID = /* @__PURE__ */ $constructor("ZodUUID", (inst, def) => {
  $ZodUUID.init(inst, def), ZodStringFormat.init(inst, def);
});
var ZodURL = /* @__PURE__ */ $constructor("ZodURL", (inst, def) => {
  $ZodURL.init(inst, def), ZodStringFormat.init(inst, def);
});
function url(params) {
  return _url(ZodURL, params);
}
var ZodEmoji = /* @__PURE__ */ $constructor("ZodEmoji", (inst, def) => {
  $ZodEmoji.init(inst, def), ZodStringFormat.init(inst, def);
});
var ZodNanoID = /* @__PURE__ */ $constructor("ZodNanoID", (inst, def) => {
  $ZodNanoID.init(inst, def), ZodStringFormat.init(inst, def);
});
var ZodCUID = /* @__PURE__ */ $constructor("ZodCUID", (inst, def) => {
  $ZodCUID.init(inst, def), ZodStringFormat.init(inst, def);
});
var ZodCUID2 = /* @__PURE__ */ $constructor("ZodCUID2", (inst, def) => {
  $ZodCUID2.init(inst, def), ZodStringFormat.init(inst, def);
});
var ZodULID = /* @__PURE__ */ $constructor("ZodULID", (inst, def) => {
  $ZodULID.init(inst, def), ZodStringFormat.init(inst, def);
});
var ZodXID = /* @__PURE__ */ $constructor("ZodXID", (inst, def) => {
  $ZodXID.init(inst, def), ZodStringFormat.init(inst, def);
});
var ZodKSUID = /* @__PURE__ */ $constructor("ZodKSUID", (inst, def) => {
  $ZodKSUID.init(inst, def), ZodStringFormat.init(inst, def);
});
var ZodIPv4 = /* @__PURE__ */ $constructor("ZodIPv4", (inst, def) => {
  $ZodIPv4.init(inst, def), ZodStringFormat.init(inst, def);
});
var ZodIPv6 = /* @__PURE__ */ $constructor("ZodIPv6", (inst, def) => {
  $ZodIPv6.init(inst, def), ZodStringFormat.init(inst, def);
});
var ZodCIDRv4 = /* @__PURE__ */ $constructor("ZodCIDRv4", (inst, def) => {
  $ZodCIDRv4.init(inst, def), ZodStringFormat.init(inst, def);
});
var ZodCIDRv6 = /* @__PURE__ */ $constructor("ZodCIDRv6", (inst, def) => {
  $ZodCIDRv6.init(inst, def), ZodStringFormat.init(inst, def);
});
var ZodBase64 = /* @__PURE__ */ $constructor("ZodBase64", (inst, def) => {
  $ZodBase64.init(inst, def), ZodStringFormat.init(inst, def);
});
var ZodBase64URL = /* @__PURE__ */ $constructor("ZodBase64URL", (inst, def) => {
  $ZodBase64URL.init(inst, def), ZodStringFormat.init(inst, def);
});
var ZodE164 = /* @__PURE__ */ $constructor("ZodE164", (inst, def) => {
  $ZodE164.init(inst, def), ZodStringFormat.init(inst, def);
});
var ZodJWT = /* @__PURE__ */ $constructor("ZodJWT", (inst, def) => {
  $ZodJWT.init(inst, def), ZodStringFormat.init(inst, def);
});
var ZodNumber = /* @__PURE__ */ $constructor("ZodNumber", (inst, def) => {
  $ZodNumber.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => numberProcessor(inst, ctx, json, params);
  let bag = inst._zod.bag;
  inst.minValue = Math.max(bag.minimum ?? Number.NEGATIVE_INFINITY, bag.exclusiveMinimum ?? Number.NEGATIVE_INFINITY) ?? null, inst.maxValue = Math.min(bag.maximum ?? Number.POSITIVE_INFINITY, bag.exclusiveMaximum ?? Number.POSITIVE_INFINITY) ?? null, inst.isInt = (bag.format ?? "").includes("int") || Number.isSafeInteger(bag.multipleOf ?? 0.5), inst.isFinite = !0, inst.format = bag.format ?? null;
}, {
  gt(value, params) {
    return this.check(_gt(value, params));
  },
  gte(value, params) {
    return this.check(_gte(value, params));
  },
  min(value, params) {
    return this.check(_gte(value, params));
  },
  lt(value, params) {
    return this.check(_lt(value, params));
  },
  lte(value, params) {
    return this.check(_lte(value, params));
  },
  max(value, params) {
    return this.check(_lte(value, params));
  },
  int(params) {
    return this.check(int(params));
  },
  safe(params) {
    return this.check(int(params));
  },
  positive(params) {
    return this.check(_gt(0, params));
  },
  nonnegative(params) {
    return this.check(_gte(0, params));
  },
  negative(params) {
    return this.check(_lt(0, params));
  },
  nonpositive(params) {
    return this.check(_lte(0, params));
  },
  multipleOf(value, params) {
    return this.check(_multipleOf(value, params));
  },
  step(value, params) {
    return this.check(_multipleOf(value, params));
  },
  finite() {
    return this;
  }
});
function number2(params) {
  return _number(ZodNumber, params);
}
var ZodNumberFormat = /* @__PURE__ */ $constructor("ZodNumberFormat", (inst, def) => {
  $ZodNumberFormat.init(inst, def), ZodNumber.init(inst, def);
});
function int(params) {
  return _int(ZodNumberFormat, params);
}
var ZodBoolean = /* @__PURE__ */ $constructor("ZodBoolean", (inst, def) => {
  $ZodBoolean.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => booleanProcessor(inst, ctx, json, params);
});
function boolean2(params) {
  return _boolean(ZodBoolean, params);
}
var ZodNull = /* @__PURE__ */ $constructor("ZodNull", (inst, def) => {
  $ZodNull.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => nullProcessor(inst, ctx, json, params);
});
function _null3(params) {
  return _null2(ZodNull, params);
}
var ZodAny = /* @__PURE__ */ $constructor("ZodAny", (inst, def) => {
  $ZodAny.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => anyProcessor(inst, ctx, json, params);
});
function any() {
  return _any(ZodAny);
}
var ZodUnknown = /* @__PURE__ */ $constructor("ZodUnknown", (inst, def) => {
  $ZodUnknown.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => unknownProcessor(inst, ctx, json, params);
});
function unknown() {
  return _unknown(ZodUnknown);
}
var ZodNever = /* @__PURE__ */ $constructor("ZodNever", (inst, def) => {
  $ZodNever.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => neverProcessor(inst, ctx, json, params);
});
function never(params) {
  return _never(ZodNever, params);
}
var ZodArray = /* @__PURE__ */ $constructor("ZodArray", (inst, def) => {
  _ensureDefaultMemoizer(), $ZodArray.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => arrayProcessor(inst, ctx, json, params), inst.element = def.element;
}, {
  min(n, params) {
    return this.check(_minLength(n, params));
  },
  nonempty(params) {
    return this.check(_minLength(1, params));
  },
  max(n, params) {
    return this.check(_maxLength(n, params));
  },
  length(n, params) {
    return this.check(_length(n, params));
  },
  unwrap() {
    return this.element;
  }
});
function array(element, params) {
  return _array(ZodArray, element, params);
}
var ZodObject = /* @__PURE__ */ $constructor("ZodObject", (inst, def) => {
  _ensureDefaultMemoizer(), $ZodObjectJIT.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => objectProcessor(inst, ctx, json, params), installLazyProp(inst, "shape", (self) => self._zod.def.shape, !1);
}, {
  keyof() {
    return _enum(Object.keys(this._zod.def.shape));
  },
  catchall(catchall) {
    return this.clone({ ...this._zod.def, catchall });
  },
  passthrough() {
    return this.clone({ ...this._zod.def, catchall: unknown() });
  },
  loose() {
    return this.clone({ ...this._zod.def, catchall: unknown() });
  },
  strict() {
    return this.clone({ ...this._zod.def, catchall: never() });
  },
  strip() {
    return this.clone({ ...this._zod.def, catchall: void 0 });
  },
  extend(incoming) {
    return extend(this, incoming);
  },
  safeExtend(incoming) {
    return safeExtend(this, incoming);
  },
  merge(other) {
    return merge(this, other);
  },
  pick(mask) {
    return pick(this, mask);
  },
  omit(mask) {
    return omit(this, mask);
  },
  partial(...args) {
    return partial(ZodOptional, this, args[0]);
  },
  exactPartial(...args) {
    return partial(ZodExactOptional, this, args[0], "exactPartial");
  },
  required(...args) {
    return required(ZodNonOptional, this, args[0]);
  }
});
function object2(shape, params) {
  let def = {
    type: "object",
    shape: shape ?? {},
    ...normalizeParams(params)
  };
  return new ZodObject(def);
}
function looseObject(shape, params) {
  return new ZodObject({
    type: "object",
    shape,
    catchall: unknown(),
    ...normalizeParams(params)
  });
}
var ZodUnion = /* @__PURE__ */ $constructor("ZodUnion", (inst, def) => {
  $ZodUnion.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => unionProcessor(inst, ctx, json, params), inst.options = def.options;
});
function union(options, params) {
  return new ZodUnion({
    type: "union",
    options,
    ...normalizeParams(params)
  });
}
var ZodDiscriminatedUnion = /* @__PURE__ */ $constructor("ZodDiscriminatedUnion", (inst, def) => {
  ZodUnion.init(inst, def), $ZodDiscriminatedUnion.init(inst, def);
});
function discriminatedUnion(discriminator, options, params) {
  return new ZodDiscriminatedUnion({
    type: "union",
    options,
    discriminator,
    ...normalizeParams(params)
  });
}
var ZodIntersection = /* @__PURE__ */ $constructor("ZodIntersection", (inst, def) => {
  $ZodIntersection.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => intersectionProcessor(inst, ctx, json, params);
});
function intersection(left, right) {
  return new ZodIntersection({
    type: "intersection",
    left,
    right
  });
}
var ZodRecord = /* @__PURE__ */ $constructor("ZodRecord", (inst, def) => {
  _ensureDefaultMemoizer(), $ZodRecord.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => recordProcessor(inst, ctx, json, params), inst.keyType = def.keyType, inst.valueType = def.valueType;
});
function record(keyType, valueType, params) {
  if (!valueType || !valueType._zod)
    return new ZodRecord({
      type: "record",
      keyType: string2(),
      valueType: keyType,
      ...normalizeParams(valueType)
    });
  return new ZodRecord({
    type: "record",
    keyType,
    valueType,
    ...normalizeParams(params)
  });
}
var ZodEnum = /* @__PURE__ */ $constructor("ZodEnum", (inst, def) => {
  $ZodEnum.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => enumProcessor(inst, ctx, json, params), inst.enum = def.entries, inst.options = Object.values(def.entries);
  let keys = new Set(Object.keys(def.entries));
  inst.extract = (values, params) => {
    let newEntries = {};
    for (let value of values)
      if (keys.has(value))
        newEntries[value] = def.entries[value];
      else
        throw Error(`Key ${value} not found in enum`);
    return new ZodEnum({
      ...def,
      checks: [],
      ...normalizeParams(params),
      entries: newEntries
    });
  }, inst.exclude = (values, params) => {
    let newEntries = { ...def.entries };
    for (let value of values)
      if (keys.has(value))
        delete newEntries[value];
      else
        throw Error(`Key ${value} not found in enum`);
    return new ZodEnum({
      ...def,
      checks: [],
      ...normalizeParams(params),
      entries: newEntries
    });
  };
});
function _enum(values, params) {
  let entries = Array.isArray(values) ? Object.fromEntries(values.map((v) => [v, v])) : values;
  return new ZodEnum({
    type: "enum",
    entries,
    ...normalizeParams(params)
  });
}
var ZodLiteral = /* @__PURE__ */ $constructor("ZodLiteral", (inst, def) => {
  $ZodLiteral.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => literalProcessor(inst, ctx, json, params), inst.values = new Set(def.values), Object.defineProperty(inst, "value", {
    get() {
      if (def.values.length > 1)
        throw Error("This schema contains multiple valid literal values. Use `.values` instead.");
      return def.values[0];
    }
  });
});
function literal(value, params) {
  return new ZodLiteral({
    type: "literal",
    values: Array.isArray(value) ? value : [value],
    ...normalizeParams(params)
  });
}
var ZodTransform = /* @__PURE__ */ $constructor("ZodTransform", (inst, def) => {
  _ensureDefaultMemoizer(), $ZodTransform.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => transformProcessor(inst, ctx, json, params), inst._zod.parse = (payload, _ctx) => {
    if (_ctx.direction === "backward")
      throw new $ZodEncodeError(inst.constructor.name);
    payload.addIssue = (issue2) => {
      if (typeof issue2 === "string")
        payload.issues.push(issue(issue2, payload.value, def));
      else {
        let _issue = issue2;
        if (_issue.fatal)
          _issue.continue = !1;
        if (_issue.code ?? (_issue.code = "custom"), !("input" in _issue))
          _issue.input = payload.value;
        _issue.inst ?? (_issue.inst = inst), payload.issues.push(issue(_issue));
      }
    };
    let output = def.transform(payload.value, payload);
    if (output instanceof Promise)
      return output.then((output) => (payload.value = output, payload));
    return payload.value = output, payload;
  };
});
function transform(fn) {
  return new ZodTransform({
    type: "transform",
    transform: fn
  });
}
var ZodOptional = /* @__PURE__ */ $constructor("ZodOptional", (inst, def) => {
  $ZodOptional.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => optionalProcessor(inst, ctx, json, params), inst.unwrap = () => inst._zod.def.innerType;
});
function optional(innerType) {
  return new ZodOptional({
    type: "optional",
    innerType
  });
}
var ZodExactOptional = /* @__PURE__ */ $constructor("ZodExactOptional", (inst, def) => {
  $ZodExactOptional.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => optionalProcessor(inst, ctx, json, params), inst.unwrap = () => inst._zod.def.innerType;
});
function exactOptional(innerType) {
  return new ZodExactOptional({
    type: "optional",
    innerType
  });
}
var ZodNullable = /* @__PURE__ */ $constructor("ZodNullable", (inst, def) => {
  $ZodNullable.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => nullableProcessor(inst, ctx, json, params), inst.unwrap = () => inst._zod.def.innerType;
});
function nullable(innerType) {
  return new ZodNullable({
    type: "nullable",
    innerType
  });
}
var ZodDefault = /* @__PURE__ */ $constructor("ZodDefault", (inst, def) => {
  $ZodDefault.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => defaultProcessor(inst, ctx, json, params), inst.unwrap = () => inst._zod.def.innerType, inst.removeDefault = inst.unwrap;
});
function _default(innerType, defaultValue) {
  return new ZodDefault({
    type: "default",
    innerType,
    get defaultValue() {
      return typeof defaultValue === "function" ? defaultValue() : shallowClone(defaultValue);
    }
  });
}
var ZodPrefault = /* @__PURE__ */ $constructor("ZodPrefault", (inst, def) => {
  $ZodPrefault.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => prefaultProcessor(inst, ctx, json, params), inst.unwrap = () => inst._zod.def.innerType;
});
function prefault(innerType, defaultValue) {
  return new ZodPrefault({
    type: "prefault",
    innerType,
    get defaultValue() {
      return typeof defaultValue === "function" ? defaultValue() : shallowClone(defaultValue);
    }
  });
}
var ZodNonOptional = /* @__PURE__ */ $constructor("ZodNonOptional", (inst, def) => {
  $ZodNonOptional.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => nonoptionalProcessor(inst, ctx, json, params), inst.unwrap = () => inst._zod.def.innerType;
});
function nonoptional(innerType, params) {
  return new ZodNonOptional({
    type: "nonoptional",
    innerType,
    ...normalizeParams(params)
  });
}
var ZodCatch = /* @__PURE__ */ $constructor("ZodCatch", (inst, def) => {
  $ZodCatch.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => catchProcessor(inst, ctx, json, params), inst.unwrap = () => inst._zod.def.innerType, inst.removeCatch = inst.unwrap;
});
function _catch(innerType, catchValue) {
  return new ZodCatch({
    type: "catch",
    innerType,
    catchValue: typeof catchValue === "function" ? catchValue : constantCatch(catchValue)
  });
}
var ZodPipe = /* @__PURE__ */ $constructor("ZodPipe", (inst, def) => {
  $ZodPipe.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => pipeProcessor(inst, ctx, json, params), inst.in = def.in, inst.out = def.out;
});
function pipe(in_, out) {
  return new ZodPipe({
    type: "pipe",
    in: in_,
    out
  });
}
var ZodPreprocess = /* @__PURE__ */ $constructor("ZodPreprocess", (inst, def) => {
  ZodPipe.init(inst, def), $ZodPreprocess.init(inst, def);
}), ZodReadonly = /* @__PURE__ */ $constructor("ZodReadonly", (inst, def) => {
  $ZodReadonly.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => readonlyProcessor(inst, ctx, json, params), inst.unwrap = () => inst._zod.def.innerType;
});
function readonly(innerType) {
  return new ZodReadonly({
    type: "readonly",
    innerType
  });
}
var ZodCustom = /* @__PURE__ */ $constructor("ZodCustom", (inst, def) => {
  $ZodCustom.init(inst, def), ZodType.init(inst, def), inst._zod.processJSONSchema = (ctx, json, params) => customProcessor(inst, ctx, json, params);
});
function custom(fn, _params) {
  return _custom(ZodCustom, fn ?? (() => !0), _params);
}
function refine(fn, _params = {}) {
  return _refine(ZodCustom, fn, _params);
}
function superRefine(fn, params) {
  return _superRefine(fn, params);
}
function preprocess(fn, schema) {
  return new ZodPreprocess({
    type: "pipe",
    in: transform(fn),
    out: schema
  });
}
// node_modules/zod/v4/classic/compat.js
var ZodIssueCode = {
  invalid_type: "invalid_type",
  too_big: "too_big",
  too_small: "too_small",
  invalid_format: "invalid_format",
  not_multiple_of: "not_multiple_of",
  unrecognized_keys: "unrecognized_keys",
  invalid_union: "invalid_union",
  invalid_key: "invalid_key",
  invalid_element: "invalid_element",
  invalid_value: "invalid_value",
  custom: "custom"
};
var ZodFirstPartyTypeKind;
(function(ZodFirstPartyTypeKind) {})(ZodFirstPartyTypeKind || (ZodFirstPartyTypeKind = {}));
// node_modules/zod/v4/classic/iso.js
function datetime2(params) {
  return _isoDateTime(ZodISODateTime, params);
}
// node_modules/zod/v4/classic/coerce.js
function number3(params) {
  return _coercedNumber(ZodNumber, params);
}
// node_modules/@modelcontextprotocol/sdk/dist/esm/types.js
var LATEST_PROTOCOL_VERSION = "2025-11-25";
var SUPPORTED_PROTOCOL_VERSIONS = [LATEST_PROTOCOL_VERSION, "2025-06-18", "2025-03-26", "2024-11-05", "2024-10-07"], RELATED_TASK_META_KEY = "io.modelcontextprotocol/related-task", JSONRPC_VERSION = "2.0", AssertObjectSchema = custom((v) => v !== null && (typeof v === "object" || typeof v === "function")), ProgressTokenSchema = union([string2(), number2().int()]), CursorSchema = string2(), TaskCreationParamsSchema = looseObject({
  ttl: number2().optional(),
  pollInterval: number2().optional()
}), TaskMetadataSchema = object2({
  ttl: number2().optional()
}), RelatedTaskMetadataSchema = object2({
  taskId: string2()
}), RequestMetaSchema = looseObject({
  progressToken: ProgressTokenSchema.optional(),
  [RELATED_TASK_META_KEY]: RelatedTaskMetadataSchema.optional()
}), BaseRequestParamsSchema = object2({
  _meta: RequestMetaSchema.optional()
}), TaskAugmentedRequestParamsSchema = BaseRequestParamsSchema.extend({
  task: TaskMetadataSchema.optional()
}), isTaskAugmentedRequestParams = (value) => TaskAugmentedRequestParamsSchema.safeParse(value).success, RequestSchema = object2({
  method: string2(),
  params: BaseRequestParamsSchema.loose().optional()
}), NotificationsParamsSchema = object2({
  _meta: RequestMetaSchema.optional()
}), NotificationSchema = object2({
  method: string2(),
  params: NotificationsParamsSchema.loose().optional()
}), ResultSchema = looseObject({
  _meta: RequestMetaSchema.optional()
}), RequestIdSchema = union([string2(), number2().int()]), JSONRPCRequestSchema = object2({
  jsonrpc: literal(JSONRPC_VERSION),
  id: RequestIdSchema,
  ...RequestSchema.shape
}).strict(), isJSONRPCRequest = (value) => JSONRPCRequestSchema.safeParse(value).success, JSONRPCNotificationSchema = object2({
  jsonrpc: literal(JSONRPC_VERSION),
  ...NotificationSchema.shape
}).strict(), isJSONRPCNotification = (value) => JSONRPCNotificationSchema.safeParse(value).success, JSONRPCResultResponseSchema = object2({
  jsonrpc: literal(JSONRPC_VERSION),
  id: RequestIdSchema,
  result: ResultSchema
}).strict(), isJSONRPCResultResponse = (value) => JSONRPCResultResponseSchema.safeParse(value).success;
var ErrorCode;
(function(ErrorCode) {
  ErrorCode[ErrorCode.ConnectionClosed = -32000] = "ConnectionClosed", ErrorCode[ErrorCode.RequestTimeout = -32001] = "RequestTimeout", ErrorCode[ErrorCode.ParseError = -32700] = "ParseError", ErrorCode[ErrorCode.InvalidRequest = -32600] = "InvalidRequest", ErrorCode[ErrorCode.MethodNotFound = -32601] = "MethodNotFound", ErrorCode[ErrorCode.InvalidParams = -32602] = "InvalidParams", ErrorCode[ErrorCode.InternalError = -32603] = "InternalError", ErrorCode[ErrorCode.UrlElicitationRequired = -32042] = "UrlElicitationRequired";
})(ErrorCode || (ErrorCode = {}));
var JSONRPCErrorResponseSchema = object2({
  jsonrpc: literal(JSONRPC_VERSION),
  id: RequestIdSchema.optional(),
  error: object2({
    code: number2().int(),
    message: string2(),
    data: unknown().optional()
  })
}).strict();
var isJSONRPCErrorResponse = (value) => JSONRPCErrorResponseSchema.safeParse(value).success;
var JSONRPCMessageSchema = union([
  JSONRPCRequestSchema,
  JSONRPCNotificationSchema,
  JSONRPCResultResponseSchema,
  JSONRPCErrorResponseSchema
]), JSONRPCResponseSchema = union([JSONRPCResultResponseSchema, JSONRPCErrorResponseSchema]), EmptyResultSchema = ResultSchema.strict(), CancelledNotificationParamsSchema = NotificationsParamsSchema.extend({
  requestId: RequestIdSchema.optional(),
  reason: string2().optional()
}), CancelledNotificationSchema = NotificationSchema.extend({
  method: literal("notifications/cancelled"),
  params: CancelledNotificationParamsSchema
}), IconSchema = object2({
  src: string2(),
  mimeType: string2().optional(),
  sizes: array(string2()).optional(),
  theme: _enum(["light", "dark"]).optional()
}), IconsSchema = object2({
  icons: array(IconSchema).optional()
}), BaseMetadataSchema = object2({
  name: string2(),
  title: string2().optional()
}), ImplementationSchema = BaseMetadataSchema.extend({
  ...BaseMetadataSchema.shape,
  ...IconsSchema.shape,
  version: string2(),
  websiteUrl: string2().optional(),
  description: string2().optional()
}), FormElicitationCapabilitySchema = intersection(object2({
  applyDefaults: boolean2().optional()
}), record(string2(), unknown())), ElicitationCapabilitySchema = preprocess((value) => {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    if (Object.keys(value).length === 0)
      return { form: {} };
  }
  return value;
}, intersection(object2({
  form: FormElicitationCapabilitySchema.optional(),
  url: AssertObjectSchema.optional()
}), record(string2(), unknown()).optional())), ClientTasksCapabilitySchema = looseObject({
  list: AssertObjectSchema.optional(),
  cancel: AssertObjectSchema.optional(),
  requests: looseObject({
    sampling: looseObject({
      createMessage: AssertObjectSchema.optional()
    }).optional(),
    elicitation: looseObject({
      create: AssertObjectSchema.optional()
    }).optional()
  }).optional()
}), ServerTasksCapabilitySchema = looseObject({
  list: AssertObjectSchema.optional(),
  cancel: AssertObjectSchema.optional(),
  requests: looseObject({
    tools: looseObject({
      call: AssertObjectSchema.optional()
    }).optional()
  }).optional()
}), ClientCapabilitiesSchema = object2({
  experimental: record(string2(), AssertObjectSchema).optional(),
  sampling: object2({
    context: AssertObjectSchema.optional(),
    tools: AssertObjectSchema.optional()
  }).optional(),
  elicitation: ElicitationCapabilitySchema.optional(),
  roots: object2({
    listChanged: boolean2().optional()
  }).optional(),
  tasks: ClientTasksCapabilitySchema.optional(),
  extensions: record(string2(), AssertObjectSchema).optional()
}), InitializeRequestParamsSchema = BaseRequestParamsSchema.extend({
  protocolVersion: string2(),
  capabilities: ClientCapabilitiesSchema,
  clientInfo: ImplementationSchema
}), InitializeRequestSchema = RequestSchema.extend({
  method: literal("initialize"),
  params: InitializeRequestParamsSchema
});
var ServerCapabilitiesSchema = object2({
  experimental: record(string2(), AssertObjectSchema).optional(),
  logging: AssertObjectSchema.optional(),
  completions: AssertObjectSchema.optional(),
  prompts: object2({
    listChanged: boolean2().optional()
  }).optional(),
  resources: object2({
    subscribe: boolean2().optional(),
    listChanged: boolean2().optional()
  }).optional(),
  tools: object2({
    listChanged: boolean2().optional()
  }).optional(),
  tasks: ServerTasksCapabilitySchema.optional(),
  extensions: record(string2(), AssertObjectSchema).optional()
}), InitializeResultSchema = ResultSchema.extend({
  protocolVersion: string2(),
  capabilities: ServerCapabilitiesSchema,
  serverInfo: ImplementationSchema,
  instructions: string2().optional()
}), InitializedNotificationSchema = NotificationSchema.extend({
  method: literal("notifications/initialized"),
  params: NotificationsParamsSchema.optional()
}), isInitializedNotification = (value) => InitializedNotificationSchema.safeParse(value).success, PingRequestSchema = RequestSchema.extend({
  method: literal("ping"),
  params: BaseRequestParamsSchema.optional()
}), ProgressSchema = object2({
  progress: number2(),
  total: optional(number2()),
  message: optional(string2())
}), ProgressNotificationParamsSchema = object2({
  ...NotificationsParamsSchema.shape,
  ...ProgressSchema.shape,
  progressToken: ProgressTokenSchema
}), ProgressNotificationSchema = NotificationSchema.extend({
  method: literal("notifications/progress"),
  params: ProgressNotificationParamsSchema
}), PaginatedRequestParamsSchema = BaseRequestParamsSchema.extend({
  cursor: CursorSchema.optional()
}), PaginatedRequestSchema = RequestSchema.extend({
  params: PaginatedRequestParamsSchema.optional()
}), PaginatedResultSchema = ResultSchema.extend({
  nextCursor: CursorSchema.optional()
}), TaskStatusSchema = _enum(["working", "input_required", "completed", "failed", "cancelled"]), TaskSchema = object2({
  taskId: string2(),
  status: TaskStatusSchema,
  ttl: union([number2(), _null3()]),
  createdAt: string2(),
  lastUpdatedAt: string2(),
  pollInterval: optional(number2()),
  statusMessage: optional(string2())
}), CreateTaskResultSchema = ResultSchema.extend({
  task: TaskSchema
}), TaskStatusNotificationParamsSchema = NotificationsParamsSchema.merge(TaskSchema), TaskStatusNotificationSchema = NotificationSchema.extend({
  method: literal("notifications/tasks/status"),
  params: TaskStatusNotificationParamsSchema
}), GetTaskRequestSchema = RequestSchema.extend({
  method: literal("tasks/get"),
  params: BaseRequestParamsSchema.extend({
    taskId: string2()
  })
}), GetTaskResultSchema = ResultSchema.merge(TaskSchema), GetTaskPayloadRequestSchema = RequestSchema.extend({
  method: literal("tasks/result"),
  params: BaseRequestParamsSchema.extend({
    taskId: string2()
  })
}), GetTaskPayloadResultSchema = ResultSchema.loose(), ListTasksRequestSchema = PaginatedRequestSchema.extend({
  method: literal("tasks/list")
}), ListTasksResultSchema = PaginatedResultSchema.extend({
  tasks: array(TaskSchema)
}), CancelTaskRequestSchema = RequestSchema.extend({
  method: literal("tasks/cancel"),
  params: BaseRequestParamsSchema.extend({
    taskId: string2()
  })
}), CancelTaskResultSchema = ResultSchema.merge(TaskSchema), ResourceContentsSchema = object2({
  uri: string2(),
  mimeType: optional(string2()),
  _meta: record(string2(), unknown()).optional()
}), TextResourceContentsSchema = ResourceContentsSchema.extend({
  text: string2()
}), Base64Schema = string2().refine((val) => {
  try {
    return atob(val), !0;
  } catch {
    return !1;
  }
}, { message: "Invalid Base64 string" }), BlobResourceContentsSchema = ResourceContentsSchema.extend({
  blob: Base64Schema
}), RoleSchema = _enum(["user", "assistant"]), AnnotationsSchema = object2({
  audience: array(RoleSchema).optional(),
  priority: number2().min(0).max(1).optional(),
  lastModified: datetime2({ offset: !0 }).optional()
}), ResourceSchema = object2({
  ...BaseMetadataSchema.shape,
  ...IconsSchema.shape,
  uri: string2(),
  description: optional(string2()),
  mimeType: optional(string2()),
  size: optional(number2()),
  annotations: AnnotationsSchema.optional(),
  _meta: optional(looseObject({}))
}), ResourceTemplateSchema = object2({
  ...BaseMetadataSchema.shape,
  ...IconsSchema.shape,
  uriTemplate: string2(),
  description: optional(string2()),
  mimeType: optional(string2()),
  annotations: AnnotationsSchema.optional(),
  _meta: optional(looseObject({}))
}), ListResourcesRequestSchema = PaginatedRequestSchema.extend({
  method: literal("resources/list")
}), ListResourcesResultSchema = PaginatedResultSchema.extend({
  resources: array(ResourceSchema)
}), ListResourceTemplatesRequestSchema = PaginatedRequestSchema.extend({
  method: literal("resources/templates/list")
}), ListResourceTemplatesResultSchema = PaginatedResultSchema.extend({
  resourceTemplates: array(ResourceTemplateSchema)
}), ResourceRequestParamsSchema = BaseRequestParamsSchema.extend({
  uri: string2()
}), ReadResourceRequestParamsSchema = ResourceRequestParamsSchema, ReadResourceRequestSchema = RequestSchema.extend({
  method: literal("resources/read"),
  params: ReadResourceRequestParamsSchema
}), ReadResourceResultSchema = ResultSchema.extend({
  contents: array(union([TextResourceContentsSchema, BlobResourceContentsSchema]))
}), ResourceListChangedNotificationSchema = NotificationSchema.extend({
  method: literal("notifications/resources/list_changed"),
  params: NotificationsParamsSchema.optional()
}), SubscribeRequestParamsSchema = ResourceRequestParamsSchema, SubscribeRequestSchema = RequestSchema.extend({
  method: literal("resources/subscribe"),
  params: SubscribeRequestParamsSchema
}), UnsubscribeRequestParamsSchema = ResourceRequestParamsSchema, UnsubscribeRequestSchema = RequestSchema.extend({
  method: literal("resources/unsubscribe"),
  params: UnsubscribeRequestParamsSchema
}), ResourceUpdatedNotificationParamsSchema = NotificationsParamsSchema.extend({
  uri: string2()
}), ResourceUpdatedNotificationSchema = NotificationSchema.extend({
  method: literal("notifications/resources/updated"),
  params: ResourceUpdatedNotificationParamsSchema
}), PromptArgumentSchema = object2({
  name: string2(),
  description: optional(string2()),
  required: optional(boolean2())
}), PromptSchema = object2({
  ...BaseMetadataSchema.shape,
  ...IconsSchema.shape,
  description: optional(string2()),
  arguments: optional(array(PromptArgumentSchema)),
  _meta: optional(looseObject({}))
}), ListPromptsRequestSchema = PaginatedRequestSchema.extend({
  method: literal("prompts/list")
}), ListPromptsResultSchema = PaginatedResultSchema.extend({
  prompts: array(PromptSchema)
}), GetPromptRequestParamsSchema = BaseRequestParamsSchema.extend({
  name: string2(),
  arguments: record(string2(), string2()).optional()
}), GetPromptRequestSchema = RequestSchema.extend({
  method: literal("prompts/get"),
  params: GetPromptRequestParamsSchema
}), TextContentSchema = object2({
  type: literal("text"),
  text: string2(),
  annotations: AnnotationsSchema.optional(),
  _meta: record(string2(), unknown()).optional()
}), ImageContentSchema = object2({
  type: literal("image"),
  data: Base64Schema,
  mimeType: string2(),
  annotations: AnnotationsSchema.optional(),
  _meta: record(string2(), unknown()).optional()
}), AudioContentSchema = object2({
  type: literal("audio"),
  data: Base64Schema,
  mimeType: string2(),
  annotations: AnnotationsSchema.optional(),
  _meta: record(string2(), unknown()).optional()
}), ToolUseContentSchema = object2({
  type: literal("tool_use"),
  name: string2(),
  id: string2(),
  input: record(string2(), unknown()),
  _meta: record(string2(), unknown()).optional()
}), EmbeddedResourceSchema = object2({
  type: literal("resource"),
  resource: union([TextResourceContentsSchema, BlobResourceContentsSchema]),
  annotations: AnnotationsSchema.optional(),
  _meta: record(string2(), unknown()).optional()
}), ResourceLinkSchema = ResourceSchema.extend({
  type: literal("resource_link")
}), ContentBlockSchema = union([
  TextContentSchema,
  ImageContentSchema,
  AudioContentSchema,
  ResourceLinkSchema,
  EmbeddedResourceSchema
]), PromptMessageSchema = object2({
  role: RoleSchema,
  content: ContentBlockSchema
}), GetPromptResultSchema = ResultSchema.extend({
  description: string2().optional(),
  messages: array(PromptMessageSchema)
}), PromptListChangedNotificationSchema = NotificationSchema.extend({
  method: literal("notifications/prompts/list_changed"),
  params: NotificationsParamsSchema.optional()
}), ToolAnnotationsSchema = object2({
  title: string2().optional(),
  readOnlyHint: boolean2().optional(),
  destructiveHint: boolean2().optional(),
  idempotentHint: boolean2().optional(),
  openWorldHint: boolean2().optional()
}), ToolExecutionSchema = object2({
  taskSupport: _enum(["required", "optional", "forbidden"]).optional()
}), ToolSchema = object2({
  ...BaseMetadataSchema.shape,
  ...IconsSchema.shape,
  description: string2().optional(),
  inputSchema: object2({
    type: literal("object"),
    properties: record(string2(), AssertObjectSchema).optional(),
    required: array(string2()).optional()
  }).catchall(unknown()),
  outputSchema: object2({
    type: literal("object"),
    properties: record(string2(), AssertObjectSchema).optional(),
    required: array(string2()).optional()
  }).catchall(unknown()).optional(),
  annotations: ToolAnnotationsSchema.optional(),
  execution: ToolExecutionSchema.optional(),
  _meta: record(string2(), unknown()).optional()
}), ListToolsRequestSchema = PaginatedRequestSchema.extend({
  method: literal("tools/list")
}), ListToolsResultSchema = PaginatedResultSchema.extend({
  tools: array(ToolSchema)
}), CallToolResultSchema = ResultSchema.extend({
  content: array(ContentBlockSchema).default([]),
  structuredContent: record(string2(), unknown()).optional(),
  isError: boolean2().optional()
}), CompatibilityCallToolResultSchema = CallToolResultSchema.or(ResultSchema.extend({
  toolResult: unknown()
})), CallToolRequestParamsSchema = TaskAugmentedRequestParamsSchema.extend({
  name: string2(),
  arguments: record(string2(), unknown()).optional()
}), CallToolRequestSchema = RequestSchema.extend({
  method: literal("tools/call"),
  params: CallToolRequestParamsSchema
}), ToolListChangedNotificationSchema = NotificationSchema.extend({
  method: literal("notifications/tools/list_changed"),
  params: NotificationsParamsSchema.optional()
}), ListChangedOptionsBaseSchema = object2({
  autoRefresh: boolean2().default(!0),
  debounceMs: number2().int().nonnegative().default(300)
}), LoggingLevelSchema = _enum(["debug", "info", "notice", "warning", "error", "critical", "alert", "emergency"]), SetLevelRequestParamsSchema = BaseRequestParamsSchema.extend({
  level: LoggingLevelSchema
}), SetLevelRequestSchema = RequestSchema.extend({
  method: literal("logging/setLevel"),
  params: SetLevelRequestParamsSchema
}), LoggingMessageNotificationParamsSchema = NotificationsParamsSchema.extend({
  level: LoggingLevelSchema,
  logger: string2().optional(),
  data: unknown()
}), LoggingMessageNotificationSchema = NotificationSchema.extend({
  method: literal("notifications/message"),
  params: LoggingMessageNotificationParamsSchema
}), ModelHintSchema = object2({
  name: string2().optional()
}), ModelPreferencesSchema = object2({
  hints: array(ModelHintSchema).optional(),
  costPriority: number2().min(0).max(1).optional(),
  speedPriority: number2().min(0).max(1).optional(),
  intelligencePriority: number2().min(0).max(1).optional()
}), ToolChoiceSchema = object2({
  mode: _enum(["auto", "required", "none"]).optional()
}), ToolResultContentSchema = object2({
  type: literal("tool_result"),
  toolUseId: string2().describe("The unique identifier for the corresponding tool call."),
  content: array(ContentBlockSchema).default([]),
  structuredContent: object2({}).loose().optional(),
  isError: boolean2().optional(),
  _meta: record(string2(), unknown()).optional()
}), SamplingContentSchema = discriminatedUnion("type", [TextContentSchema, ImageContentSchema, AudioContentSchema]), SamplingMessageContentBlockSchema = discriminatedUnion("type", [
  TextContentSchema,
  ImageContentSchema,
  AudioContentSchema,
  ToolUseContentSchema,
  ToolResultContentSchema
]), SamplingMessageSchema = object2({
  role: RoleSchema,
  content: union([SamplingMessageContentBlockSchema, array(SamplingMessageContentBlockSchema)]),
  _meta: record(string2(), unknown()).optional()
}), CreateMessageRequestParamsSchema = TaskAugmentedRequestParamsSchema.extend({
  messages: array(SamplingMessageSchema),
  modelPreferences: ModelPreferencesSchema.optional(),
  systemPrompt: string2().optional(),
  includeContext: _enum(["none", "thisServer", "allServers"]).optional(),
  temperature: number2().optional(),
  maxTokens: number2().int(),
  stopSequences: array(string2()).optional(),
  metadata: AssertObjectSchema.optional(),
  tools: array(ToolSchema).optional(),
  toolChoice: ToolChoiceSchema.optional()
}), CreateMessageRequestSchema = RequestSchema.extend({
  method: literal("sampling/createMessage"),
  params: CreateMessageRequestParamsSchema
}), CreateMessageResultSchema = ResultSchema.extend({
  model: string2(),
  stopReason: optional(_enum(["endTurn", "stopSequence", "maxTokens"]).or(string2())),
  role: RoleSchema,
  content: SamplingContentSchema
}), CreateMessageResultWithToolsSchema = ResultSchema.extend({
  model: string2(),
  stopReason: optional(_enum(["endTurn", "stopSequence", "maxTokens", "toolUse"]).or(string2())),
  role: RoleSchema,
  content: union([SamplingMessageContentBlockSchema, array(SamplingMessageContentBlockSchema)])
}), BooleanSchemaSchema = object2({
  type: literal("boolean"),
  title: string2().optional(),
  description: string2().optional(),
  default: boolean2().optional()
}), StringSchemaSchema = object2({
  type: literal("string"),
  title: string2().optional(),
  description: string2().optional(),
  minLength: number2().optional(),
  maxLength: number2().optional(),
  format: _enum(["email", "uri", "date", "date-time"]).optional(),
  default: string2().optional()
}), NumberSchemaSchema = object2({
  type: _enum(["number", "integer"]),
  title: string2().optional(),
  description: string2().optional(),
  minimum: number2().optional(),
  maximum: number2().optional(),
  default: number2().optional()
}), UntitledSingleSelectEnumSchemaSchema = object2({
  type: literal("string"),
  title: string2().optional(),
  description: string2().optional(),
  enum: array(string2()),
  default: string2().optional()
}), TitledSingleSelectEnumSchemaSchema = object2({
  type: literal("string"),
  title: string2().optional(),
  description: string2().optional(),
  oneOf: array(object2({
    const: string2(),
    title: string2()
  })),
  default: string2().optional()
}), LegacyTitledEnumSchemaSchema = object2({
  type: literal("string"),
  title: string2().optional(),
  description: string2().optional(),
  enum: array(string2()),
  enumNames: array(string2()).optional(),
  default: string2().optional()
}), SingleSelectEnumSchemaSchema = union([UntitledSingleSelectEnumSchemaSchema, TitledSingleSelectEnumSchemaSchema]), UntitledMultiSelectEnumSchemaSchema = object2({
  type: literal("array"),
  title: string2().optional(),
  description: string2().optional(),
  minItems: number2().optional(),
  maxItems: number2().optional(),
  items: object2({
    type: literal("string"),
    enum: array(string2())
  }),
  default: array(string2()).optional()
}), TitledMultiSelectEnumSchemaSchema = object2({
  type: literal("array"),
  title: string2().optional(),
  description: string2().optional(),
  minItems: number2().optional(),
  maxItems: number2().optional(),
  items: object2({
    anyOf: array(object2({
      const: string2(),
      title: string2()
    }))
  }),
  default: array(string2()).optional()
}), MultiSelectEnumSchemaSchema = union([UntitledMultiSelectEnumSchemaSchema, TitledMultiSelectEnumSchemaSchema]), EnumSchemaSchema = union([LegacyTitledEnumSchemaSchema, SingleSelectEnumSchemaSchema, MultiSelectEnumSchemaSchema]), PrimitiveSchemaDefinitionSchema = union([EnumSchemaSchema, BooleanSchemaSchema, StringSchemaSchema, NumberSchemaSchema]), ElicitRequestFormParamsSchema = TaskAugmentedRequestParamsSchema.extend({
  mode: literal("form").optional(),
  message: string2(),
  requestedSchema: object2({
    type: literal("object"),
    properties: record(string2(), PrimitiveSchemaDefinitionSchema),
    required: array(string2()).optional()
  })
}), ElicitRequestURLParamsSchema = TaskAugmentedRequestParamsSchema.extend({
  mode: literal("url"),
  message: string2(),
  elicitationId: string2(),
  url: string2().url()
}), ElicitRequestParamsSchema = union([ElicitRequestFormParamsSchema, ElicitRequestURLParamsSchema]), ElicitRequestSchema = RequestSchema.extend({
  method: literal("elicitation/create"),
  params: ElicitRequestParamsSchema
}), ElicitationCompleteNotificationParamsSchema = NotificationsParamsSchema.extend({
  elicitationId: string2()
}), ElicitationCompleteNotificationSchema = NotificationSchema.extend({
  method: literal("notifications/elicitation/complete"),
  params: ElicitationCompleteNotificationParamsSchema
}), ElicitResultSchema = ResultSchema.extend({
  action: _enum(["accept", "decline", "cancel"]),
  content: preprocess((val) => val === null ? void 0 : val, record(string2(), union([string2(), number2(), boolean2(), array(string2())])).optional())
}), ResourceTemplateReferenceSchema = object2({
  type: literal("ref/resource"),
  uri: string2()
});
var PromptReferenceSchema = object2({
  type: literal("ref/prompt"),
  name: string2()
}), CompleteRequestParamsSchema = BaseRequestParamsSchema.extend({
  ref: union([PromptReferenceSchema, ResourceTemplateReferenceSchema]),
  argument: object2({
    name: string2(),
    value: string2()
  }),
  context: object2({
    arguments: record(string2(), string2()).optional()
  }).optional()
}), CompleteRequestSchema = RequestSchema.extend({
  method: literal("completion/complete"),
  params: CompleteRequestParamsSchema
});
var CompleteResultSchema = ResultSchema.extend({
  completion: looseObject({
    values: array(string2()).max(100),
    total: optional(number2().int()),
    hasMore: optional(boolean2())
  })
}), RootSchema = object2({
  uri: string2().startsWith("file://"),
  name: string2().optional(),
  _meta: record(string2(), unknown()).optional()
}), ListRootsRequestSchema = RequestSchema.extend({
  method: literal("roots/list"),
  params: BaseRequestParamsSchema.optional()
}), ListRootsResultSchema = ResultSchema.extend({
  roots: array(RootSchema)
}), RootsListChangedNotificationSchema = NotificationSchema.extend({
  method: literal("notifications/roots/list_changed"),
  params: NotificationsParamsSchema.optional()
}), ClientRequestSchema = union([
  PingRequestSchema,
  InitializeRequestSchema,
  CompleteRequestSchema,
  SetLevelRequestSchema,
  GetPromptRequestSchema,
  ListPromptsRequestSchema,
  ListResourcesRequestSchema,
  ListResourceTemplatesRequestSchema,
  ReadResourceRequestSchema,
  SubscribeRequestSchema,
  UnsubscribeRequestSchema,
  CallToolRequestSchema,
  ListToolsRequestSchema,
  GetTaskRequestSchema,
  GetTaskPayloadRequestSchema,
  ListTasksRequestSchema,
  CancelTaskRequestSchema
]), ClientNotificationSchema = union([
  CancelledNotificationSchema,
  ProgressNotificationSchema,
  InitializedNotificationSchema,
  RootsListChangedNotificationSchema,
  TaskStatusNotificationSchema
]), ClientResultSchema = union([
  EmptyResultSchema,
  CreateMessageResultSchema,
  CreateMessageResultWithToolsSchema,
  ElicitResultSchema,
  ListRootsResultSchema,
  GetTaskResultSchema,
  ListTasksResultSchema,
  CreateTaskResultSchema
]), ServerRequestSchema = union([
  PingRequestSchema,
  CreateMessageRequestSchema,
  ElicitRequestSchema,
  ListRootsRequestSchema,
  GetTaskRequestSchema,
  GetTaskPayloadRequestSchema,
  ListTasksRequestSchema,
  CancelTaskRequestSchema
]), ServerNotificationSchema = union([
  CancelledNotificationSchema,
  ProgressNotificationSchema,
  LoggingMessageNotificationSchema,
  ResourceUpdatedNotificationSchema,
  ResourceListChangedNotificationSchema,
  ToolListChangedNotificationSchema,
  PromptListChangedNotificationSchema,
  TaskStatusNotificationSchema,
  ElicitationCompleteNotificationSchema
]), ServerResultSchema = union([
  EmptyResultSchema,
  InitializeResultSchema,
  CompleteResultSchema,
  GetPromptResultSchema,
  ListPromptsResultSchema,
  ListResourcesResultSchema,
  ListResourceTemplatesResultSchema,
  ReadResourceResultSchema,
  CallToolResultSchema,
  ListToolsResultSchema,
  GetTaskResultSchema,
  ListTasksResultSchema,
  CreateTaskResultSchema
]);

class McpError extends Error {
  constructor(code, message, data) {
    super(`MCP error ${code}: ${message}`);
    this.code = code, this.data = data, this.name = "McpError";
  }
  static fromError(code, message, data) {
    if (code === ErrorCode.UrlElicitationRequired && data) {
      let errorData = data;
      if (errorData.elicitations)
        return new UrlElicitationRequiredError(errorData.elicitations, message);
    }
    return new McpError(code, message, data);
  }
}

class UrlElicitationRequiredError extends McpError {
  constructor(elicitations, message = `URL elicitation${elicitations.length > 1 ? "s" : ""} required`) {
    super(ErrorCode.UrlElicitationRequired, message, {
      elicitations
    });
  }
  get elicitations() {
    return this.data?.elicitations ?? [];
  }
}

// node_modules/@modelcontextprotocol/sdk/dist/esm/experimental/tasks/interfaces.js
function isTerminal(status) {
  return status === "completed" || status === "failed" || status === "cancelled";
}

// node_modules/zod-to-json-schema/dist/esm/Options.js
var ignoreOverride = Symbol("Let zodToJsonSchema decide on which parser to use");
// node_modules/zod-to-json-schema/dist/esm/parsers/string.js
var ALPHA_NUMERIC = new Set("ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvxyz0123456789");
// node_modules/@modelcontextprotocol/sdk/dist/esm/server/zod-json-schema-compat.js
function getMethodLiteral(schema) {
  let methodSchema = getObjectShape(schema)?.method;
  if (!methodSchema)
    throw Error("Schema is missing a method literal");
  let value = getLiteralValue(methodSchema);
  if (typeof value !== "string")
    throw Error("Schema method literal must be a string");
  return value;
}
function parseWithCompat(schema, data) {
  let result = safeParse2(schema, data);
  if (!result.success)
    throw result.error;
  return result.data;
}

// node_modules/@modelcontextprotocol/sdk/dist/esm/shared/protocol.js
var DEFAULT_REQUEST_TIMEOUT_MSEC = 60000;

class Protocol {
  constructor(_options) {
    if (this._options = _options, this._requestMessageId = 0, this._requestHandlers = /* @__PURE__ */ new Map, this._requestHandlerAbortControllers = /* @__PURE__ */ new Map, this._notificationHandlers = /* @__PURE__ */ new Map, this._responseHandlers = /* @__PURE__ */ new Map, this._progressHandlers = /* @__PURE__ */ new Map, this._timeoutInfo = /* @__PURE__ */ new Map, this._pendingDebouncedNotifications = /* @__PURE__ */ new Set, this._taskProgressTokens = /* @__PURE__ */ new Map, this._requestResolvers = /* @__PURE__ */ new Map, this.setNotificationHandler(CancelledNotificationSchema, (notification) => {
      this._oncancel(notification);
    }), this.setNotificationHandler(ProgressNotificationSchema, (notification) => {
      this._onprogress(notification);
    }), this.setRequestHandler(PingRequestSchema, (_request) => ({})), this._taskStore = _options?.taskStore, this._taskMessageQueue = _options?.taskMessageQueue, this._taskStore)
      this.setRequestHandler(GetTaskRequestSchema, async (request, extra) => {
        let task = await this._taskStore.getTask(request.params.taskId, extra.sessionId);
        if (!task)
          throw new McpError(ErrorCode.InvalidParams, "Failed to retrieve task: Task not found");
        return {
          ...task
        };
      }), this.setRequestHandler(GetTaskPayloadRequestSchema, async (request, extra) => {
        let handleTaskResult = async () => {
          let taskId = request.params.taskId;
          if (this._taskMessageQueue) {
            let queuedMessage;
            while (queuedMessage = await this._taskMessageQueue.dequeue(taskId, extra.sessionId)) {
              if (queuedMessage.type === "response" || queuedMessage.type === "error") {
                let message = queuedMessage.message, requestId = message.id, resolver = this._requestResolvers.get(requestId);
                if (resolver)
                  if (this._requestResolvers.delete(requestId), queuedMessage.type === "response")
                    resolver(message);
                  else {
                    let errorMessage = message, error = new McpError(errorMessage.error.code, errorMessage.error.message, errorMessage.error.data);
                    resolver(error);
                  }
                else {
                  let messageType = queuedMessage.type === "response" ? "Response" : "Error";
                  this._onerror(Error(`${messageType} handler missing for request ${requestId}`));
                }
                continue;
              }
              await this._transport?.send(queuedMessage.message, { relatedRequestId: extra.requestId });
            }
          }
          let task = await this._taskStore.getTask(taskId, extra.sessionId);
          if (!task)
            throw new McpError(ErrorCode.InvalidParams, `Task not found: ${taskId}`);
          if (!isTerminal(task.status))
            return await this._waitForTaskUpdate(taskId, extra.signal), await handleTaskResult();
          if (isTerminal(task.status)) {
            let result = await this._taskStore.getTaskResult(taskId, extra.sessionId);
            return this._clearTaskQueue(taskId), {
              ...result,
              _meta: {
                ...result._meta,
                [RELATED_TASK_META_KEY]: {
                  taskId
                }
              }
            };
          }
          return await handleTaskResult();
        };
        return await handleTaskResult();
      }), this.setRequestHandler(ListTasksRequestSchema, async (request, extra) => {
        try {
          let { tasks, nextCursor } = await this._taskStore.listTasks(request.params?.cursor, extra.sessionId);
          return {
            tasks,
            nextCursor,
            _meta: {}
          };
        } catch (error) {
          throw new McpError(ErrorCode.InvalidParams, `Failed to list tasks: ${error instanceof Error ? error.message : String(error)}`);
        }
      }), this.setRequestHandler(CancelTaskRequestSchema, async (request, extra) => {
        try {
          let task = await this._taskStore.getTask(request.params.taskId, extra.sessionId);
          if (!task)
            throw new McpError(ErrorCode.InvalidParams, `Task not found: ${request.params.taskId}`);
          if (isTerminal(task.status))
            throw new McpError(ErrorCode.InvalidParams, `Cannot cancel task in terminal status: ${task.status}`);
          await this._taskStore.updateTaskStatus(request.params.taskId, "cancelled", "Client cancelled task execution.", extra.sessionId), this._clearTaskQueue(request.params.taskId);
          let cancelledTask = await this._taskStore.getTask(request.params.taskId, extra.sessionId);
          if (!cancelledTask)
            throw new McpError(ErrorCode.InvalidParams, `Task not found after cancellation: ${request.params.taskId}`);
          return {
            _meta: {},
            ...cancelledTask
          };
        } catch (error) {
          if (error instanceof McpError)
            throw error;
          throw new McpError(ErrorCode.InvalidRequest, `Failed to cancel task: ${error instanceof Error ? error.message : String(error)}`);
        }
      });
  }
  async _oncancel(notification) {
    if (!notification.params.requestId)
      return;
    this._requestHandlerAbortControllers.get(notification.params.requestId)?.abort(notification.params.reason);
  }
  _setupTimeout(messageId, timeout, maxTotalTimeout, onTimeout, resetTimeoutOnProgress = !1) {
    this._timeoutInfo.set(messageId, {
      timeoutId: setTimeout(onTimeout, timeout),
      startTime: Date.now(),
      timeout,
      maxTotalTimeout,
      resetTimeoutOnProgress,
      onTimeout
    });
  }
  _resetTimeout(messageId) {
    let info = this._timeoutInfo.get(messageId);
    if (!info)
      return !1;
    let totalElapsed = Date.now() - info.startTime;
    if (info.maxTotalTimeout && totalElapsed >= info.maxTotalTimeout)
      throw this._timeoutInfo.delete(messageId), McpError.fromError(ErrorCode.RequestTimeout, "Maximum total timeout exceeded", {
        maxTotalTimeout: info.maxTotalTimeout,
        totalElapsed
      });
    return clearTimeout(info.timeoutId), info.timeoutId = setTimeout(info.onTimeout, info.timeout), !0;
  }
  _cleanupTimeout(messageId) {
    let info = this._timeoutInfo.get(messageId);
    if (info)
      clearTimeout(info.timeoutId), this._timeoutInfo.delete(messageId);
  }
  async connect(transport) {
    if (this._transport)
      throw Error("Already connected to a transport. Call close() before connecting to a new transport, or use a separate Protocol instance per connection.");
    this._transport = transport;
    let _onclose = this.transport?.onclose;
    this._transport.onclose = () => {
      _onclose?.(), this._onclose();
    };
    let _onerror = this.transport?.onerror;
    this._transport.onerror = (error) => {
      _onerror?.(error), this._onerror(error);
    };
    let _onmessage = this._transport?.onmessage;
    this._transport.onmessage = (message, extra) => {
      if (_onmessage?.(message, extra), isJSONRPCResultResponse(message) || isJSONRPCErrorResponse(message))
        this._onresponse(message);
      else if (isJSONRPCRequest(message))
        this._onrequest(message, extra);
      else if (isJSONRPCNotification(message))
        this._onnotification(message);
      else
        this._onerror(Error(`Unknown message type: ${JSON.stringify(message)}`));
    }, await this._transport.start();
  }
  _onclose() {
    let responseHandlers = this._responseHandlers;
    this._responseHandlers = /* @__PURE__ */ new Map, this._progressHandlers.clear(), this._taskProgressTokens.clear(), this._pendingDebouncedNotifications.clear();
    for (let info of this._timeoutInfo.values())
      clearTimeout(info.timeoutId);
    this._timeoutInfo.clear();
    for (let controller of this._requestHandlerAbortControllers.values())
      controller.abort();
    this._requestHandlerAbortControllers.clear();
    let error = McpError.fromError(ErrorCode.ConnectionClosed, "Connection closed");
    this._transport = void 0, this.onclose?.();
    for (let handler of responseHandlers.values())
      handler(error);
  }
  _onerror(error) {
    this.onerror?.(error);
  }
  _onnotification(notification) {
    let handler = this._notificationHandlers.get(notification.method) ?? this.fallbackNotificationHandler;
    if (handler === void 0)
      return;
    Promise.resolve().then(() => handler(notification)).catch((error) => this._onerror(Error(`Uncaught error in notification handler: ${error}`)));
  }
  _onrequest(request, extra) {
    let handler = this._requestHandlers.get(request.method) ?? this.fallbackRequestHandler, capturedTransport = this._transport, relatedTaskId = request.params?._meta?.[RELATED_TASK_META_KEY]?.taskId;
    if (handler === void 0) {
      let errorResponse = {
        jsonrpc: "2.0",
        id: request.id,
        error: {
          code: ErrorCode.MethodNotFound,
          message: "Method not found"
        }
      };
      if (relatedTaskId && this._taskMessageQueue)
        this._enqueueTaskMessage(relatedTaskId, {
          type: "error",
          message: errorResponse,
          timestamp: Date.now()
        }, capturedTransport?.sessionId).catch((error) => this._onerror(Error(`Failed to enqueue error response: ${error}`)));
      else
        capturedTransport?.send(errorResponse).catch((error) => this._onerror(Error(`Failed to send an error response: ${error}`)));
      return;
    }
    let abortController = new AbortController;
    this._requestHandlerAbortControllers.set(request.id, abortController);
    let taskCreationParams = isTaskAugmentedRequestParams(request.params) ? request.params.task : void 0, taskStore = this._taskStore ? this.requestTaskStore(request, capturedTransport?.sessionId) : void 0, fullExtra = {
      signal: abortController.signal,
      sessionId: capturedTransport?.sessionId,
      _meta: request.params?._meta,
      sendNotification: async (notification) => {
        if (abortController.signal.aborted)
          return;
        let notificationOptions = { relatedRequestId: request.id };
        if (relatedTaskId)
          notificationOptions.relatedTask = { taskId: relatedTaskId };
        await this.notification(notification, notificationOptions);
      },
      sendRequest: async (r, resultSchema, options) => {
        if (abortController.signal.aborted)
          throw new McpError(ErrorCode.ConnectionClosed, "Request was cancelled");
        let requestOptions = { ...options, relatedRequestId: request.id };
        if (relatedTaskId && !requestOptions.relatedTask)
          requestOptions.relatedTask = { taskId: relatedTaskId };
        let effectiveTaskId = requestOptions.relatedTask?.taskId ?? relatedTaskId;
        if (effectiveTaskId && taskStore)
          await taskStore.updateTaskStatus(effectiveTaskId, "input_required");
        return await this.request(r, resultSchema, requestOptions);
      },
      authInfo: extra?.authInfo,
      requestId: request.id,
      requestInfo: extra?.requestInfo,
      taskId: relatedTaskId,
      taskStore,
      taskRequestedTtl: taskCreationParams?.ttl,
      closeSSEStream: extra?.closeSSEStream,
      closeStandaloneSSEStream: extra?.closeStandaloneSSEStream
    };
    Promise.resolve().then(() => {
      if (taskCreationParams)
        this.assertTaskHandlerCapability(request.method);
    }).then(() => handler(request, fullExtra)).then(async (result) => {
      if (abortController.signal.aborted)
        return;
      let response = {
        result,
        jsonrpc: "2.0",
        id: request.id
      };
      if (relatedTaskId && this._taskMessageQueue)
        await this._enqueueTaskMessage(relatedTaskId, {
          type: "response",
          message: response,
          timestamp: Date.now()
        }, capturedTransport?.sessionId);
      else
        await capturedTransport?.send(response);
    }, async (error) => {
      if (abortController.signal.aborted)
        return;
      let errorResponse = {
        jsonrpc: "2.0",
        id: request.id,
        error: {
          code: Number.isSafeInteger(error.code) ? error.code : ErrorCode.InternalError,
          message: error.message ?? "Internal error",
          ...error.data !== void 0 && { data: error.data }
        }
      };
      if (relatedTaskId && this._taskMessageQueue)
        await this._enqueueTaskMessage(relatedTaskId, {
          type: "error",
          message: errorResponse,
          timestamp: Date.now()
        }, capturedTransport?.sessionId);
      else
        await capturedTransport?.send(errorResponse);
    }).catch((error) => this._onerror(Error(`Failed to send response: ${error}`))).finally(() => {
      if (this._requestHandlerAbortControllers.get(request.id) === abortController)
        this._requestHandlerAbortControllers.delete(request.id);
    });
  }
  _onprogress(notification) {
    let { progressToken, ...params } = notification.params, messageId = Number(progressToken), handler = this._progressHandlers.get(messageId);
    if (!handler) {
      this._onerror(Error(`Received a progress notification for an unknown token: ${JSON.stringify(notification)}`));
      return;
    }
    let responseHandler = this._responseHandlers.get(messageId), timeoutInfo = this._timeoutInfo.get(messageId);
    if (timeoutInfo && responseHandler && timeoutInfo.resetTimeoutOnProgress)
      try {
        this._resetTimeout(messageId);
      } catch (error) {
        this._responseHandlers.delete(messageId), this._progressHandlers.delete(messageId), this._cleanupTimeout(messageId), responseHandler(error);
        return;
      }
    handler(params);
  }
  _onresponse(response) {
    let messageId = Number(response.id), resolver = this._requestResolvers.get(messageId);
    if (resolver) {
      if (this._requestResolvers.delete(messageId), isJSONRPCResultResponse(response))
        resolver(response);
      else {
        let error = new McpError(response.error.code, response.error.message, response.error.data);
        resolver(error);
      }
      return;
    }
    let handler = this._responseHandlers.get(messageId);
    if (handler === void 0) {
      this._onerror(Error(`Received a response for an unknown message ID: ${JSON.stringify(response)}`));
      return;
    }
    this._responseHandlers.delete(messageId), this._cleanupTimeout(messageId);
    let isTaskResponse = !1;
    if (isJSONRPCResultResponse(response) && response.result && typeof response.result === "object") {
      let result = response.result;
      if (result.task && typeof result.task === "object") {
        let task = result.task;
        if (typeof task.taskId === "string")
          isTaskResponse = !0, this._taskProgressTokens.set(task.taskId, messageId);
      }
    }
    if (!isTaskResponse)
      this._progressHandlers.delete(messageId);
    if (isJSONRPCResultResponse(response))
      handler(response);
    else {
      let error = McpError.fromError(response.error.code, response.error.message, response.error.data);
      handler(error);
    }
  }
  get transport() {
    return this._transport;
  }
  async close() {
    await this._transport?.close();
  }
  async* requestStream(request, resultSchema, options) {
    let { task } = options ?? {};
    if (!task) {
      try {
        yield { type: "result", result: await this.request(request, resultSchema, options) };
      } catch (error) {
        yield {
          type: "error",
          error: error instanceof McpError ? error : new McpError(ErrorCode.InternalError, String(error))
        };
      }
      return;
    }
    let taskId;
    try {
      let createResult = await this.request(request, CreateTaskResultSchema, options);
      if (createResult.task)
        taskId = createResult.task.taskId, yield { type: "taskCreated", task: createResult.task };
      else
        throw new McpError(ErrorCode.InternalError, "Task creation did not return a task");
      while (!0) {
        let task = await this.getTask({ taskId }, options);
        if (yield { type: "taskStatus", task }, isTerminal(task.status)) {
          if (task.status === "completed")
            yield { type: "result", result: await this.getTaskResult({ taskId }, resultSchema, options) };
          else if (task.status === "failed")
            yield {
              type: "error",
              error: new McpError(ErrorCode.InternalError, `Task ${taskId} failed`)
            };
          else if (task.status === "cancelled")
            yield {
              type: "error",
              error: new McpError(ErrorCode.InternalError, `Task ${taskId} was cancelled`)
            };
          return;
        }
        if (task.status === "input_required") {
          yield { type: "result", result: await this.getTaskResult({ taskId }, resultSchema, options) };
          return;
        }
        let pollInterval = task.pollInterval ?? this._options?.defaultTaskPollInterval ?? 1000;
        await new Promise((resolve) => setTimeout(resolve, pollInterval)), options?.signal?.throwIfAborted();
      }
    } catch (error) {
      yield {
        type: "error",
        error: error instanceof McpError ? error : new McpError(ErrorCode.InternalError, String(error))
      };
    }
  }
  request(request, resultSchema, options) {
    let { relatedRequestId, resumptionToken, onresumptiontoken, task, relatedTask } = options ?? {};
    return new Promise((resolve, reject) => {
      let earlyReject = (error) => {
        reject(error);
      };
      if (!this._transport) {
        earlyReject(Error("Not connected"));
        return;
      }
      if (this._options?.enforceStrictCapabilities === !0)
        try {
          if (this.assertCapabilityForMethod(request.method), task)
            this.assertTaskCapability(request.method);
        } catch (e) {
          earlyReject(e);
          return;
        }
      options?.signal?.throwIfAborted();
      let messageId = this._requestMessageId++, jsonrpcRequest = {
        ...request,
        jsonrpc: "2.0",
        id: messageId
      };
      if (options?.onprogress)
        this._progressHandlers.set(messageId, options.onprogress), jsonrpcRequest.params = {
          ...request.params,
          _meta: {
            ...request.params?._meta || {},
            progressToken: messageId
          }
        };
      if (task)
        jsonrpcRequest.params = {
          ...jsonrpcRequest.params,
          task
        };
      if (relatedTask)
        jsonrpcRequest.params = {
          ...jsonrpcRequest.params,
          _meta: {
            ...jsonrpcRequest.params?._meta || {},
            [RELATED_TASK_META_KEY]: relatedTask
          }
        };
      let cancel = (reason) => {
        this._responseHandlers.delete(messageId), this._progressHandlers.delete(messageId), this._cleanupTimeout(messageId), this._transport?.send({
          jsonrpc: "2.0",
          method: "notifications/cancelled",
          params: {
            requestId: messageId,
            reason: String(reason)
          }
        }, { relatedRequestId, resumptionToken, onresumptiontoken }).catch((error) => this._onerror(Error(`Failed to send cancellation: ${error}`)));
        let error = reason instanceof McpError ? reason : new McpError(ErrorCode.RequestTimeout, String(reason));
        reject(error);
      };
      this._responseHandlers.set(messageId, (response) => {
        if (options?.signal?.aborted)
          return;
        if (response instanceof Error)
          return reject(response);
        try {
          let parseResult = safeParse2(resultSchema, response.result);
          if (!parseResult.success)
            reject(parseResult.error);
          else
            resolve(parseResult.data);
        } catch (error) {
          reject(error);
        }
      }), options?.signal?.addEventListener("abort", () => {
        cancel(options?.signal?.reason);
      });
      let timeout = options?.timeout ?? DEFAULT_REQUEST_TIMEOUT_MSEC, timeoutHandler = () => cancel(McpError.fromError(ErrorCode.RequestTimeout, "Request timed out", { timeout }));
      this._setupTimeout(messageId, timeout, options?.maxTotalTimeout, timeoutHandler, options?.resetTimeoutOnProgress ?? !1);
      let relatedTaskId = relatedTask?.taskId;
      if (relatedTaskId) {
        let responseResolver = (response) => {
          let handler = this._responseHandlers.get(messageId);
          if (handler)
            handler(response);
          else
            this._onerror(Error(`Response handler missing for side-channeled request ${messageId}`));
        };
        this._requestResolvers.set(messageId, responseResolver), this._enqueueTaskMessage(relatedTaskId, {
          type: "request",
          message: jsonrpcRequest,
          timestamp: Date.now()
        }).catch((error) => {
          this._cleanupTimeout(messageId), reject(error);
        });
      } else
        this._transport.send(jsonrpcRequest, { relatedRequestId, resumptionToken, onresumptiontoken }).catch((error) => {
          this._cleanupTimeout(messageId), reject(error);
        });
    });
  }
  async getTask(params, options) {
    return this.request({ method: "tasks/get", params }, GetTaskResultSchema, options);
  }
  async getTaskResult(params, resultSchema, options) {
    return this.request({ method: "tasks/result", params }, resultSchema, options);
  }
  async listTasks(params, options) {
    return this.request({ method: "tasks/list", params }, ListTasksResultSchema, options);
  }
  async cancelTask(params, options) {
    return this.request({ method: "tasks/cancel", params }, CancelTaskResultSchema, options);
  }
  async notification(notification, options) {
    if (!this._transport)
      throw Error("Not connected");
    this.assertNotificationCapability(notification.method);
    let relatedTaskId = options?.relatedTask?.taskId;
    if (relatedTaskId) {
      let jsonrpcNotification = {
        ...notification,
        jsonrpc: "2.0",
        params: {
          ...notification.params,
          _meta: {
            ...notification.params?._meta || {},
            [RELATED_TASK_META_KEY]: options.relatedTask
          }
        }
      };
      await this._enqueueTaskMessage(relatedTaskId, {
        type: "notification",
        message: jsonrpcNotification,
        timestamp: Date.now()
      });
      return;
    }
    if ((this._options?.debouncedNotificationMethods ?? []).includes(notification.method) && !notification.params && !options?.relatedRequestId && !options?.relatedTask) {
      if (this._pendingDebouncedNotifications.has(notification.method))
        return;
      this._pendingDebouncedNotifications.add(notification.method), Promise.resolve().then(() => {
        if (this._pendingDebouncedNotifications.delete(notification.method), !this._transport)
          return;
        let jsonrpcNotification = {
          ...notification,
          jsonrpc: "2.0"
        };
        if (options?.relatedTask)
          jsonrpcNotification = {
            ...jsonrpcNotification,
            params: {
              ...jsonrpcNotification.params,
              _meta: {
                ...jsonrpcNotification.params?._meta || {},
                [RELATED_TASK_META_KEY]: options.relatedTask
              }
            }
          };
        this._transport?.send(jsonrpcNotification, options).catch((error) => this._onerror(error));
      });
      return;
    }
    let jsonrpcNotification = {
      ...notification,
      jsonrpc: "2.0"
    };
    if (options?.relatedTask)
      jsonrpcNotification = {
        ...jsonrpcNotification,
        params: {
          ...jsonrpcNotification.params,
          _meta: {
            ...jsonrpcNotification.params?._meta || {},
            [RELATED_TASK_META_KEY]: options.relatedTask
          }
        }
      };
    await this._transport.send(jsonrpcNotification, options);
  }
  setRequestHandler(requestSchema, handler) {
    let method = getMethodLiteral(requestSchema);
    this.assertRequestHandlerCapability(method), this._requestHandlers.set(method, (request, extra) => {
      let parsed = parseWithCompat(requestSchema, request);
      return Promise.resolve(handler(parsed, extra));
    });
  }
  removeRequestHandler(method) {
    this._requestHandlers.delete(method);
  }
  assertCanSetRequestHandler(method) {
    if (this._requestHandlers.has(method))
      throw Error(`A request handler for ${method} already exists, which would be overridden`);
  }
  setNotificationHandler(notificationSchema, handler) {
    let method = getMethodLiteral(notificationSchema);
    this._notificationHandlers.set(method, (notification) => {
      let parsed = parseWithCompat(notificationSchema, notification);
      return Promise.resolve(handler(parsed));
    });
  }
  removeNotificationHandler(method) {
    this._notificationHandlers.delete(method);
  }
  _cleanupTaskProgressHandler(taskId) {
    let progressToken = this._taskProgressTokens.get(taskId);
    if (progressToken !== void 0)
      this._progressHandlers.delete(progressToken), this._taskProgressTokens.delete(taskId);
  }
  async _enqueueTaskMessage(taskId, message, sessionId) {
    if (!this._taskStore || !this._taskMessageQueue)
      throw Error("Cannot enqueue task message: taskStore and taskMessageQueue are not configured");
    let maxQueueSize = this._options?.maxTaskQueueSize;
    await this._taskMessageQueue.enqueue(taskId, message, sessionId, maxQueueSize);
  }
  async _clearTaskQueue(taskId, sessionId) {
    if (this._taskMessageQueue) {
      let messages = await this._taskMessageQueue.dequeueAll(taskId, sessionId);
      for (let message of messages)
        if (message.type === "request" && isJSONRPCRequest(message.message)) {
          let requestId = message.message.id, resolver = this._requestResolvers.get(requestId);
          if (resolver)
            resolver(new McpError(ErrorCode.InternalError, "Task cancelled or completed")), this._requestResolvers.delete(requestId);
          else
            this._onerror(Error(`Resolver missing for request ${requestId} during task ${taskId} cleanup`));
        }
    }
  }
  async _waitForTaskUpdate(taskId, signal) {
    let interval = this._options?.defaultTaskPollInterval ?? 1000;
    try {
      let task = await this._taskStore?.getTask(taskId);
      if (task?.pollInterval)
        interval = task.pollInterval;
    } catch {}
    return new Promise((resolve, reject) => {
      if (signal.aborted) {
        reject(new McpError(ErrorCode.InvalidRequest, "Request cancelled"));
        return;
      }
      let timeoutId = setTimeout(resolve, interval);
      signal.addEventListener("abort", () => {
        clearTimeout(timeoutId), reject(new McpError(ErrorCode.InvalidRequest, "Request cancelled"));
      }, { once: !0 });
    });
  }
  requestTaskStore(request, sessionId) {
    let taskStore = this._taskStore;
    if (!taskStore)
      throw Error("No task store configured");
    return {
      createTask: async (taskParams) => {
        if (!request)
          throw Error("No request provided");
        return await taskStore.createTask(taskParams, request.id, {
          method: request.method,
          params: request.params
        }, sessionId);
      },
      getTask: async (taskId) => {
        let task = await taskStore.getTask(taskId, sessionId);
        if (!task)
          throw new McpError(ErrorCode.InvalidParams, "Failed to retrieve task: Task not found");
        return task;
      },
      storeTaskResult: async (taskId, status, result) => {
        await taskStore.storeTaskResult(taskId, status, result, sessionId);
        let task = await taskStore.getTask(taskId, sessionId);
        if (task) {
          let notification = TaskStatusNotificationSchema.parse({
            method: "notifications/tasks/status",
            params: task
          });
          if (await this.notification(notification), isTerminal(task.status))
            this._cleanupTaskProgressHandler(taskId);
        }
      },
      getTaskResult: (taskId) => taskStore.getTaskResult(taskId, sessionId),
      updateTaskStatus: async (taskId, status, statusMessage) => {
        let task = await taskStore.getTask(taskId, sessionId);
        if (!task)
          throw new McpError(ErrorCode.InvalidParams, `Task "${taskId}" not found - it may have been cleaned up`);
        if (isTerminal(task.status))
          throw new McpError(ErrorCode.InvalidParams, `Cannot update task "${taskId}" from terminal status "${task.status}" to "${status}". Terminal states (completed, failed, cancelled) cannot transition to other states.`);
        await taskStore.updateTaskStatus(taskId, status, statusMessage, sessionId);
        let updatedTask = await taskStore.getTask(taskId, sessionId);
        if (updatedTask) {
          let notification = TaskStatusNotificationSchema.parse({
            method: "notifications/tasks/status",
            params: updatedTask
          });
          if (await this.notification(notification), isTerminal(updatedTask.status))
            this._cleanupTaskProgressHandler(taskId);
        }
      },
      listTasks: (cursor) => taskStore.listTasks(cursor, sessionId)
    };
  }
}
function isPlainObject2(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}
function mergeCapabilities(base, additional) {
  let result = { ...base };
  for (let key in additional) {
    let k = key, addValue = additional[k];
    if (addValue === void 0)
      continue;
    let baseValue = result[k];
    if (isPlainObject2(baseValue) && isPlainObject2(addValue))
      result[k] = { ...baseValue, ...addValue };
    else
      result[k] = addValue;
  }
  return result;
}

// node_modules/@modelcontextprotocol/sdk/dist/esm/validation/ajv-provider.js
var import_ajv = __toESM(require_ajv(), 1), import_ajv_formats = __toESM(require_dist(), 1);
function createDefaultAjvInstance() {
  let ajv = new import_ajv.default({
    strict: !1,
    validateFormats: !0,
    validateSchema: !1,
    allErrors: !0
  });
  return import_ajv_formats.default(ajv), ajv;
}

class AjvJsonSchemaValidator {
  constructor(ajv) {
    this._ajv = ajv ?? createDefaultAjvInstance();
  }
  getValidator(schema) {
    let ajvValidator = "$id" in schema && typeof schema.$id === "string" ? this._ajv.getSchema(schema.$id) ?? this._ajv.compile(schema) : this._ajv.compile(schema);
    return (input) => {
      if (ajvValidator(input))
        return {
          valid: !0,
          data: input,
          errorMessage: void 0
        };
      else
        return {
          valid: !1,
          data: void 0,
          errorMessage: this._ajv.errorsText(ajvValidator.errors)
        };
    };
  }
}

// node_modules/@modelcontextprotocol/sdk/dist/esm/experimental/tasks/server.js
class ExperimentalServerTasks {
  constructor(_server) {
    this._server = _server;
  }
  requestStream(request, resultSchema, options) {
    return this._server.requestStream(request, resultSchema, options);
  }
  createMessageStream(params, options) {
    let clientCapabilities = this._server.getClientCapabilities();
    if ((params.tools || params.toolChoice) && !clientCapabilities?.sampling?.tools)
      throw Error("Client does not support sampling tools capability.");
    if (params.messages.length > 0) {
      let lastMessage = params.messages[params.messages.length - 1], lastContent = Array.isArray(lastMessage.content) ? lastMessage.content : [lastMessage.content], hasToolResults = lastContent.some((c) => c.type === "tool_result"), previousMessage = params.messages.length > 1 ? params.messages[params.messages.length - 2] : void 0, previousContent = previousMessage ? Array.isArray(previousMessage.content) ? previousMessage.content : [previousMessage.content] : [], hasPreviousToolUse = previousContent.some((c) => c.type === "tool_use");
      if (hasToolResults) {
        if (lastContent.some((c) => c.type !== "tool_result"))
          throw Error("The last message must contain only tool_result content if any is present");
        if (!hasPreviousToolUse)
          throw Error("tool_result blocks are not matching any tool_use from the previous message");
      }
      if (hasPreviousToolUse) {
        let toolUseIds = new Set(previousContent.filter((c) => c.type === "tool_use").map((c) => c.id)), toolResultIds = new Set(lastContent.filter((c) => c.type === "tool_result").map((c) => c.toolUseId));
        if (toolUseIds.size !== toolResultIds.size || ![...toolUseIds].every((id) => toolResultIds.has(id)))
          throw Error("ids of tool_result blocks and tool_use blocks from previous message do not match");
      }
    }
    return this.requestStream({
      method: "sampling/createMessage",
      params
    }, CreateMessageResultSchema, options);
  }
  elicitInputStream(params, options) {
    let clientCapabilities = this._server.getClientCapabilities(), mode = params.mode ?? "form";
    switch (mode) {
      case "url": {
        if (!clientCapabilities?.elicitation?.url)
          throw Error("Client does not support url elicitation.");
        break;
      }
      case "form": {
        if (!clientCapabilities?.elicitation?.form)
          throw Error("Client does not support form elicitation.");
        break;
      }
    }
    let normalizedParams = mode === "form" && params.mode === void 0 ? { ...params, mode: "form" } : params;
    return this.requestStream({
      method: "elicitation/create",
      params: normalizedParams
    }, ElicitResultSchema, options);
  }
  async getTask(taskId, options) {
    return this._server.getTask({ taskId }, options);
  }
  async getTaskResult(taskId, resultSchema, options) {
    return this._server.getTaskResult({ taskId }, resultSchema, options);
  }
  async listTasks(cursor, options) {
    return this._server.listTasks(cursor ? { cursor } : void 0, options);
  }
  async cancelTask(taskId, options) {
    return this._server.cancelTask({ taskId }, options);
  }
}

// node_modules/@modelcontextprotocol/sdk/dist/esm/experimental/tasks/helpers.js
function assertToolsCallTaskCapability(requests, method, entityName) {
  if (!requests)
    throw Error(`${entityName} does not support task creation (required for ${method})`);
  switch (method) {
    case "tools/call":
      if (!requests.tools?.call)
        throw Error(`${entityName} does not support task creation for tools/call (required for ${method})`);
      break;
    default:
      break;
  }
}
function assertClientRequestTaskCapability(requests, method, entityName) {
  if (!requests)
    throw Error(`${entityName} does not support task creation (required for ${method})`);
  switch (method) {
    case "sampling/createMessage":
      if (!requests.sampling?.createMessage)
        throw Error(`${entityName} does not support task creation for sampling/createMessage (required for ${method})`);
      break;
    case "elicitation/create":
      if (!requests.elicitation?.create)
        throw Error(`${entityName} does not support task creation for elicitation/create (required for ${method})`);
      break;
    default:
      break;
  }
}

// node_modules/@modelcontextprotocol/sdk/dist/esm/server/index.js
class Server extends Protocol {
  constructor(_serverInfo, options) {
    super(options);
    if (this._serverInfo = _serverInfo, this._loggingLevels = /* @__PURE__ */ new Map, this.LOG_LEVEL_SEVERITY = new Map(LoggingLevelSchema.options.map((level, index) => [level, index])), this.isMessageIgnored = (level, sessionId) => {
      let currentLevel = this._loggingLevels.get(sessionId);
      return currentLevel ? this.LOG_LEVEL_SEVERITY.get(level) < this.LOG_LEVEL_SEVERITY.get(currentLevel) : !1;
    }, this._capabilities = options?.capabilities ?? {}, this._instructions = options?.instructions, this._jsonSchemaValidator = options?.jsonSchemaValidator ?? new AjvJsonSchemaValidator, this.setRequestHandler(InitializeRequestSchema, (request) => this._oninitialize(request)), this.setNotificationHandler(InitializedNotificationSchema, () => this.oninitialized?.()), this._capabilities.logging)
      this.setRequestHandler(SetLevelRequestSchema, async (request, extra) => {
        let transportSessionId = extra.sessionId || extra.requestInfo?.headers["mcp-session-id"] || void 0, { level } = request.params, parseResult = LoggingLevelSchema.safeParse(level);
        if (parseResult.success)
          this._loggingLevels.set(transportSessionId, parseResult.data);
        return {};
      });
  }
  get experimental() {
    if (!this._experimental)
      this._experimental = {
        tasks: new ExperimentalServerTasks(this)
      };
    return this._experimental;
  }
  registerCapabilities(capabilities) {
    if (this.transport)
      throw Error("Cannot register capabilities after connecting to transport");
    this._capabilities = mergeCapabilities(this._capabilities, capabilities);
  }
  setRequestHandler(requestSchema, handler) {
    let methodSchema = getObjectShape(requestSchema)?.method;
    if (!methodSchema)
      throw Error("Schema is missing a method literal");
    let methodValue = getLiteralValue(methodSchema);
    if (typeof methodValue !== "string")
      throw Error("Schema method literal must be a string");
    if (methodValue === "tools/call") {
      let wrappedHandler = async (request, extra) => {
        let validatedRequest = safeParse2(CallToolRequestSchema, request);
        if (!validatedRequest.success) {
          let errorMessage = validatedRequest.error instanceof Error ? validatedRequest.error.message : String(validatedRequest.error);
          throw new McpError(ErrorCode.InvalidParams, `Invalid tools/call request: ${errorMessage}`);
        }
        let { params } = validatedRequest.data, result = await Promise.resolve(handler(request, extra));
        if (params.task) {
          let taskValidationResult = safeParse2(CreateTaskResultSchema, result);
          if (!taskValidationResult.success) {
            let errorMessage = taskValidationResult.error instanceof Error ? taskValidationResult.error.message : String(taskValidationResult.error);
            throw new McpError(ErrorCode.InvalidParams, `Invalid task creation result: ${errorMessage}`);
          }
          return taskValidationResult.data;
        }
        let validationResult = safeParse2(CallToolResultSchema, result);
        if (!validationResult.success) {
          let errorMessage = validationResult.error instanceof Error ? validationResult.error.message : String(validationResult.error);
          throw new McpError(ErrorCode.InvalidParams, `Invalid tools/call result: ${errorMessage}`);
        }
        return validationResult.data;
      };
      return super.setRequestHandler(requestSchema, wrappedHandler);
    }
    return super.setRequestHandler(requestSchema, handler);
  }
  assertCapabilityForMethod(method) {
    switch (method) {
      case "sampling/createMessage":
        if (!this._clientCapabilities?.sampling)
          throw Error(`Client does not support sampling (required for ${method})`);
        break;
      case "elicitation/create":
        if (!this._clientCapabilities?.elicitation)
          throw Error(`Client does not support elicitation (required for ${method})`);
        break;
      case "roots/list":
        if (!this._clientCapabilities?.roots)
          throw Error(`Client does not support listing roots (required for ${method})`);
        break;
      case "ping":
        break;
    }
  }
  assertNotificationCapability(method) {
    switch (method) {
      case "notifications/message":
        if (!this._capabilities.logging)
          throw Error(`Server does not support logging (required for ${method})`);
        break;
      case "notifications/resources/updated":
      case "notifications/resources/list_changed":
        if (!this._capabilities.resources)
          throw Error(`Server does not support notifying about resources (required for ${method})`);
        break;
      case "notifications/tools/list_changed":
        if (!this._capabilities.tools)
          throw Error(`Server does not support notifying of tool list changes (required for ${method})`);
        break;
      case "notifications/prompts/list_changed":
        if (!this._capabilities.prompts)
          throw Error(`Server does not support notifying of prompt list changes (required for ${method})`);
        break;
      case "notifications/elicitation/complete":
        if (!this._clientCapabilities?.elicitation?.url)
          throw Error(`Client does not support URL elicitation (required for ${method})`);
        break;
      case "notifications/cancelled":
        break;
      case "notifications/progress":
        break;
    }
  }
  assertRequestHandlerCapability(method) {
    if (!this._capabilities)
      return;
    switch (method) {
      case "completion/complete":
        if (!this._capabilities.completions)
          throw Error(`Server does not support completions (required for ${method})`);
        break;
      case "logging/setLevel":
        if (!this._capabilities.logging)
          throw Error(`Server does not support logging (required for ${method})`);
        break;
      case "prompts/get":
      case "prompts/list":
        if (!this._capabilities.prompts)
          throw Error(`Server does not support prompts (required for ${method})`);
        break;
      case "resources/list":
      case "resources/templates/list":
      case "resources/read":
        if (!this._capabilities.resources)
          throw Error(`Server does not support resources (required for ${method})`);
        break;
      case "tools/call":
      case "tools/list":
        if (!this._capabilities.tools)
          throw Error(`Server does not support tools (required for ${method})`);
        break;
      case "tasks/get":
      case "tasks/list":
      case "tasks/result":
      case "tasks/cancel":
        if (!this._capabilities.tasks)
          throw Error(`Server does not support tasks capability (required for ${method})`);
        break;
      case "ping":
      case "initialize":
        break;
    }
  }
  assertTaskCapability(method) {
    assertClientRequestTaskCapability(this._clientCapabilities?.tasks?.requests, method, "Client");
  }
  assertTaskHandlerCapability(method) {
    if (!this._capabilities)
      return;
    assertToolsCallTaskCapability(this._capabilities.tasks?.requests, method, "Server");
  }
  async _oninitialize(request) {
    let requestedVersion = request.params.protocolVersion;
    return this._clientCapabilities = request.params.capabilities, this._clientVersion = request.params.clientInfo, {
      protocolVersion: SUPPORTED_PROTOCOL_VERSIONS.includes(requestedVersion) ? requestedVersion : LATEST_PROTOCOL_VERSION,
      capabilities: this.getCapabilities(),
      serverInfo: this._serverInfo,
      ...this._instructions && { instructions: this._instructions }
    };
  }
  getClientCapabilities() {
    return this._clientCapabilities;
  }
  getClientVersion() {
    return this._clientVersion;
  }
  getCapabilities() {
    return this._capabilities;
  }
  async ping() {
    return this.request({ method: "ping" }, EmptyResultSchema);
  }
  async createMessage(params, options) {
    if (params.tools || params.toolChoice) {
      if (!this._clientCapabilities?.sampling?.tools)
        throw Error("Client does not support sampling tools capability.");
    }
    if (params.messages.length > 0) {
      let lastMessage = params.messages[params.messages.length - 1], lastContent = Array.isArray(lastMessage.content) ? lastMessage.content : [lastMessage.content], hasToolResults = lastContent.some((c) => c.type === "tool_result"), previousMessage = params.messages.length > 1 ? params.messages[params.messages.length - 2] : void 0, previousContent = previousMessage ? Array.isArray(previousMessage.content) ? previousMessage.content : [previousMessage.content] : [], hasPreviousToolUse = previousContent.some((c) => c.type === "tool_use");
      if (hasToolResults) {
        if (lastContent.some((c) => c.type !== "tool_result"))
          throw Error("The last message must contain only tool_result content if any is present");
        if (!hasPreviousToolUse)
          throw Error("tool_result blocks are not matching any tool_use from the previous message");
      }
      if (hasPreviousToolUse) {
        let toolUseIds = new Set(previousContent.filter((c) => c.type === "tool_use").map((c) => c.id)), toolResultIds = new Set(lastContent.filter((c) => c.type === "tool_result").map((c) => c.toolUseId));
        if (toolUseIds.size !== toolResultIds.size || ![...toolUseIds].every((id) => toolResultIds.has(id)))
          throw Error("ids of tool_result blocks and tool_use blocks from previous message do not match");
      }
    }
    if (params.tools)
      return this.request({ method: "sampling/createMessage", params }, CreateMessageResultWithToolsSchema, options);
    return this.request({ method: "sampling/createMessage", params }, CreateMessageResultSchema, options);
  }
  async elicitInput(params, options) {
    switch (params.mode ?? "form") {
      case "url": {
        if (!this._clientCapabilities?.elicitation?.url)
          throw Error("Client does not support url elicitation.");
        let urlParams = params;
        return this.request({ method: "elicitation/create", params: urlParams }, ElicitResultSchema, options);
      }
      case "form": {
        if (!this._clientCapabilities?.elicitation?.form)
          throw Error("Client does not support form elicitation.");
        let formParams = params.mode === "form" ? params : { ...params, mode: "form" }, result = await this.request({ method: "elicitation/create", params: formParams }, ElicitResultSchema, options);
        if (result.action === "accept" && result.content && formParams.requestedSchema)
          try {
            let validationResult = this._jsonSchemaValidator.getValidator(formParams.requestedSchema)(result.content);
            if (!validationResult.valid)
              throw new McpError(ErrorCode.InvalidParams, `Elicitation response content does not match requested schema: ${validationResult.errorMessage}`);
          } catch (error) {
            if (error instanceof McpError)
              throw error;
            throw new McpError(ErrorCode.InternalError, `Error validating elicitation response: ${error instanceof Error ? error.message : String(error)}`);
          }
        return result;
      }
    }
  }
  createElicitationCompletionNotifier(elicitationId, options) {
    if (!this._clientCapabilities?.elicitation?.url)
      throw Error("Client does not support URL elicitation (required for notifications/elicitation/complete)");
    return () => this.notification({
      method: "notifications/elicitation/complete",
      params: {
        elicitationId
      }
    }, options);
  }
  async listRoots(params, options) {
    return this.request({ method: "roots/list", params }, ListRootsResultSchema, options);
  }
  async sendLoggingMessage(params, sessionId) {
    if (this._capabilities.logging) {
      if (!this.isMessageIgnored(params.level, sessionId))
        return this.notification({ method: "notifications/message", params });
    }
  }
  async sendResourceUpdated(params) {
    return this.notification({
      method: "notifications/resources/updated",
      params
    });
  }
  async sendResourceListChanged() {
    return this.notification({
      method: "notifications/resources/list_changed"
    });
  }
  async sendToolListChanged() {
    return this.notification({ method: "notifications/tools/list_changed" });
  }
  async sendPromptListChanged() {
    return this.notification({ method: "notifications/prompts/list_changed" });
  }
}

// node_modules/@modelcontextprotocol/sdk/dist/esm/server/stdio.js
import process3 from "process";

// node_modules/@modelcontextprotocol/sdk/dist/esm/shared/stdio.js
var STDIO_DEFAULT_MAX_BUFFER_SIZE = 10485760;

class ReadBuffer {
  constructor(options) {
    this._maxBufferSize = options?.maxBufferSize ?? STDIO_DEFAULT_MAX_BUFFER_SIZE;
  }
  append(chunk) {
    if ((this._buffer?.length ?? 0) + chunk.length > this._maxBufferSize)
      throw this.clear(), Error(`ReadBuffer exceeded maximum size of ${this._maxBufferSize} bytes`);
    this._buffer = this._buffer ? Buffer.concat([this._buffer, chunk]) : chunk;
  }
  readMessage() {
    if (!this._buffer)
      return null;
    let index = this._buffer.indexOf(`
`);
    if (index === -1)
      return null;
    let line = this._buffer.toString("utf8", 0, index).replace(/\r$/, "");
    return this._buffer = this._buffer.subarray(index + 1), deserializeMessage(line);
  }
  clear() {
    this._buffer = void 0;
  }
}
function deserializeMessage(line) {
  return JSONRPCMessageSchema.parse(JSON.parse(line));
}
function serializeMessage(message) {
  return JSON.stringify(message) + `
`;
}

// node_modules/@modelcontextprotocol/sdk/dist/esm/server/stdio.js
class StdioServerTransport {
  constructor(_stdin = process3.stdin, _stdout = process3.stdout, options) {
    this._stdin = _stdin, this._stdout = _stdout, this._started = !1, this._ondata = (chunk) => {
      try {
        this._readBuffer.append(chunk), this.processReadBuffer();
      } catch (error) {
        this.onerror?.(error), this.close().catch(() => {});
      }
    }, this._onerror = (error) => {
      this.onerror?.(error);
    }, this._readBuffer = new ReadBuffer({ maxBufferSize: options?.maxBufferSize });
  }
  async start() {
    if (this._started)
      throw Error("StdioServerTransport already started! If using Server class, note that connect() calls start() automatically.");
    this._started = !0, this._stdin.on("data", this._ondata), this._stdin.on("error", this._onerror);
  }
  processReadBuffer() {
    while (!0)
      try {
        let message = this._readBuffer.readMessage();
        if (message === null)
          break;
        this.onmessage?.(message);
      } catch (error) {
        this.onerror?.(error);
      }
  }
  async close() {
    if (this._stdin.off("data", this._ondata), this._stdin.off("error", this._onerror), this._stdin.listenerCount("data") === 0)
      this._stdin.pause();
    this._readBuffer.clear(), this.onclose?.();
  }
  send(message) {
    return new Promise((resolve) => {
      let json = serializeMessage(message);
      if (this._stdout.write(json))
        resolve();
      else
        this._stdout.once("drain", resolve);
    });
  }
}

// ../shared/mcp-rpc.mjs
import { appendFile, writeFile } from "fs/promises";
var LOG_FILE = process.env.MCP_LOG;
function logProgress(message) {
  let timestamp = (/* @__PURE__ */ new Date()).toISOString().substring(11, 19);
  process.stderr.write(`[${timestamp}] ${message}
`);
}

class FileLogger {
  constructor(logFile, flushIntervalMs = 500) {
    this.logFile = logFile, this.buffer = [], this.flushInterval = flushIntervalMs, this.flushTimer = null, this.writing = !1;
  }
  log(message) {
    if (!this.logFile)
      return;
    let timestamp = (/* @__PURE__ */ new Date()).toISOString().substring(11, 19);
    this.buffer.push(`[${timestamp}] ${message}`), this.scheduleFlush();
  }
  scheduleFlush() {
    if (this.flushTimer)
      return;
    this.flushTimer = setTimeout(() => this.flush(), this.flushInterval);
  }
  async flush() {
    if (this.flushTimer = null, this.writing || this.buffer.length === 0)
      return;
    this.writing = !0;
    let lines = this.buffer.splice(0);
    try {
      await appendFile(this.logFile, lines.join(`
`) + `
`);
    } catch {}
    if (this.writing = !1, this.buffer.length > 0)
      this.scheduleFlush();
  }
}
var fileLogger = null;
function logToFile(message) {
  if (!LOG_FILE)
    return;
  if (!fileLogger)
    fileLogger = new FileLogger(LOG_FILE);
  fileLogger.log(message);
}
async function clearLogFile() {
  if (!LOG_FILE)
    return;
  try {
    await writeFile(LOG_FILE, "");
  } catch {}
}

// stream-transport.ts
import { request as httpRequest } from "http";
import { request as httpsRequest } from "https";
import { Readable } from "stream";

// node_modules/is-network-error/index.js
var objectToString = Object.prototype.toString, isError = (value) => objectToString.call(value) === "[object Error]", errorMessages2 = /* @__PURE__ */ new Set([
  "network error",
  "Failed to fetch",
  "NetworkError when attempting to fetch resource.",
  "The Internet connection appears to be offline.",
  "Network request failed",
  "fetch failed",
  "terminated",
  " A network error occurred.",
  "Network connection lost"
]);
function isNetworkError(error) {
  if (!(error && isError(error) && error.name === "TypeError" && typeof error.message === "string"))
    return !1;
  let { message, stack } = error;
  if (message === "Load failed")
    return stack === void 0 || "__sentry_captured__" in error;
  if (message.startsWith("error sending request for url"))
    return !0;
  return errorMessages2.has(message);
}

// node_modules/p-retry/index.js
function validateRetries(retries) {
  if (typeof retries === "number") {
    if (retries < 0)
      throw TypeError("Expected `retries` to be a non-negative number.");
    if (Number.isNaN(retries))
      throw TypeError("Expected `retries` to be a valid number or Infinity, got NaN.");
  } else if (retries !== void 0)
    throw TypeError("Expected `retries` to be a number or Infinity.");
}
function validateNumberOption(name, value, { min = 0, allowInfinity = !1 } = {}) {
  if (value === void 0)
    return;
  if (typeof value !== "number" || Number.isNaN(value))
    throw TypeError(`Expected \`${name}\` to be a number${allowInfinity ? " or Infinity" : ""}.`);
  if (!allowInfinity && !Number.isFinite(value))
    throw TypeError(`Expected \`${name}\` to be a finite number.`);
  if (value < min)
    throw TypeError(`Expected \`${name}\` to be \u2265 ${min}.`);
}

class AbortError extends Error {
  constructor(message) {
    super();
    if (message instanceof Error)
      this.originalError = message, { message } = message;
    else
      this.originalError = Error(message), this.originalError.stack = this.stack;
    this.name = "AbortError", this.message = message;
  }
}
function calculateDelay(retriesConsumed, options) {
  let attempt = Math.max(1, retriesConsumed + 1), random = options.randomize ? Math.random() + 1 : 1, timeout = Math.round(random * options.minTimeout * options.factor ** (attempt - 1));
  return timeout = Math.min(timeout, options.maxTimeout), timeout;
}
function calculateRemainingTime(start, max) {
  if (!Number.isFinite(max))
    return max;
  return max - (performance.now() - start);
}
async function onAttemptFailure({ error, attemptNumber, retriesConsumed, startTime, options }) {
  let normalizedError = error instanceof Error ? error : TypeError(`Non-error was thrown: "${error}". You should only throw errors.`);
  if (normalizedError instanceof AbortError)
    throw normalizedError.originalError;
  let retriesLeft = Number.isFinite(options.retries) ? Math.max(0, options.retries - retriesConsumed) : options.retries, maxRetryTime = options.maxRetryTime ?? Number.POSITIVE_INFINITY, context = Object.freeze({
    error: normalizedError,
    attemptNumber,
    retriesLeft,
    retriesConsumed
  });
  if (await options.onFailedAttempt(context), calculateRemainingTime(startTime, maxRetryTime) <= 0)
    throw normalizedError;
  let consumeRetry = await options.shouldConsumeRetry(context), remainingTime = calculateRemainingTime(startTime, maxRetryTime);
  if (remainingTime <= 0 || retriesLeft <= 0)
    throw normalizedError;
  if (normalizedError instanceof TypeError && !isNetworkError(normalizedError)) {
    if (consumeRetry)
      throw normalizedError;
    return options.signal?.throwIfAborted(), !1;
  }
  if (!await options.shouldRetry(context))
    throw normalizedError;
  if (!consumeRetry)
    return options.signal?.throwIfAborted(), !1;
  let delayTime = calculateDelay(retriesConsumed, options), finalDelay = Math.min(delayTime, remainingTime);
  if (options.signal?.throwIfAborted(), finalDelay > 0)
    await new Promise((resolve, reject) => {
      let onAbort = () => {
        clearTimeout(timeoutToken), options.signal?.removeEventListener("abort", onAbort), reject(options.signal.reason);
      }, timeoutToken = setTimeout(() => {
        options.signal?.removeEventListener("abort", onAbort), resolve();
      }, finalDelay);
      if (options.unref)
        timeoutToken.unref?.();
      options.signal?.addEventListener("abort", onAbort, { once: !0 });
    });
  return options.signal?.throwIfAborted(), !0;
}
async function pRetry(input, options = {}) {
  if (options = { ...options }, validateRetries(options.retries), Object.hasOwn(options, "forever"))
    throw Error("The `forever` option is no longer supported. For many use-cases, you can set `retries: Infinity` instead.");
  if (options.retries ??= 10, options.factor ??= 2, options.minTimeout ??= 1000, options.maxTimeout ??= Number.POSITIVE_INFINITY, options.maxRetryTime ??= Number.POSITIVE_INFINITY, options.randomize ??= !1, options.onFailedAttempt ??= () => {}, options.shouldRetry ??= () => !0, options.shouldConsumeRetry ??= () => !0, validateNumberOption("factor", options.factor, { min: 0, allowInfinity: !1 }), validateNumberOption("minTimeout", options.minTimeout, { min: 0, allowInfinity: !1 }), validateNumberOption("maxTimeout", options.maxTimeout, { min: 0, allowInfinity: !0 }), validateNumberOption("maxRetryTime", options.maxRetryTime, { min: 0, allowInfinity: !0 }), !(options.factor > 0))
    options.factor = 1;
  options.signal?.throwIfAborted();
  let attemptNumber = 0, retriesConsumed = 0, startTime = performance.now();
  while (Number.isFinite(options.retries) ? retriesConsumed <= options.retries : !0) {
    attemptNumber++;
    try {
      options.signal?.throwIfAborted();
      let result = await input(attemptNumber);
      return options.signal?.throwIfAborted(), result;
    } catch (error) {
      if (await onAttemptFailure({
        error,
        attemptNumber,
        retriesConsumed,
        startTime,
        options
      }))
        retriesConsumed++;
    }
  }
  throw Error("Retry attempts exhausted without throwing an error.");
}

// node_modules/content-type/index.js
/*!
 * content-type
 * Copyright(c) 2015 Douglas Christopher Wilson
 * MIT Licensed
 */
var PARAM_REGEXP = /; *([!#$%&'*+.^_`|~0-9A-Za-z-]+) *= *("(?:[\u000b\u0020\u0021\u0023-\u005b\u005d-\u007e\u0080-\u00ff]|\\[\u000b\u0020-\u00ff])*"|[!#$%&'*+.^_`|~0-9A-Za-z-]+) */g;
var QESC_REGEXP = /\\([\u000b\u0020-\u00ff])/g;
var TYPE_REGEXP = /^[!#$%&'*+.^_`|~0-9A-Za-z-]+\/[!#$%&'*+.^_`|~0-9A-Za-z-]+$/;
var $parse = parse5;
function parse5(string) {
  if (!string)
    throw TypeError("argument string is required");
  var header = typeof string === "object" ? getcontenttype(string) : string;
  if (typeof header !== "string")
    throw TypeError("argument string is required to be a string");
  var index = header.indexOf(";"), type = index !== -1 ? header.slice(0, index).trim() : header.trim();
  if (!TYPE_REGEXP.test(type))
    throw TypeError("invalid media type");
  var obj = new ContentType(type.toLowerCase());
  if (index !== -1) {
    var key, match, value;
    PARAM_REGEXP.lastIndex = index;
    while (match = PARAM_REGEXP.exec(header)) {
      if (match.index !== index)
        throw TypeError("invalid parameter format");
      if (index += match[0].length, key = match[1].toLowerCase(), value = match[2], value.charCodeAt(0) === 34) {
        if (value = value.slice(1, -1), value.indexOf("\\") !== -1)
          value = value.replace(QESC_REGEXP, "$1");
      }
      obj.parameters[key] = value;
    }
    if (index !== header.length)
      throw TypeError("invalid parameter format");
  }
  return obj;
}
function getcontenttype(obj) {
  var header;
  if (typeof obj.getHeader === "function")
    header = obj.getHeader("content-type");
  else if (typeof obj.headers === "object")
    header = obj.headers && obj.headers["content-type"];
  if (typeof header !== "string")
    throw TypeError("content-type header is missing from object");
  return header;
}
function ContentType(type) {
  this.parameters = Object.create(null), this.type = type;
}

// node_modules/@modelcontextprotocol/sdk/dist/esm/shared/mediaType.js
function mediaTypeEssence(header) {
  if (!header)
    return;
  try {
    return $parse(header).type;
  } catch {
    let essence = (header.split(";", 1)[0] ?? "").trim().toLowerCase();
    if (essence === "" || header.slice(essence.length).includes(","))
      return;
    return essence;
  }
}

// node_modules/@modelcontextprotocol/sdk/dist/esm/shared/transport.js
function normalizeHeaders(headers) {
  if (!headers)
    return {};
  if (headers instanceof Headers)
    return Object.fromEntries(headers.entries());
  if (Array.isArray(headers))
    return Object.fromEntries(headers);
  return { ...headers };
}
function createFetchWithInit(baseFetch = fetch, baseInit) {
  if (!baseInit)
    return baseFetch;
  return async (url, init) => {
    let mergedInit = {
      ...baseInit,
      ...init,
      headers: init?.headers ? { ...normalizeHeaders(baseInit.headers), ...normalizeHeaders(init.headers) } : baseInit.headers
    };
    return baseFetch(url, mergedInit);
  };
}

// node_modules/pkce-challenge/dist/index.node.js
var crypto;
crypto = globalThis.crypto?.webcrypto ?? globalThis.crypto ?? import("crypto").then((m) => m.webcrypto);
async function getRandomValues(size) {
  return (await crypto).getRandomValues(new Uint8Array(size));
}
async function random(size) {
  let evenDistCutoff = Math.pow(2, 8) - Math.pow(2, 8) % 66, result = "";
  while (result.length < size) {
    let randomBytes = await getRandomValues(size - result.length);
    for (let randomByte of randomBytes)
      if (randomByte < evenDistCutoff)
        result += "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"[randomByte % 66];
  }
  return result;
}
async function generateVerifier(length) {
  return await random(length);
}
async function generateChallenge(code_verifier) {
  let buffer = await (await crypto).subtle.digest("SHA-256", (/* @__PURE__ */ new TextEncoder()).encode(code_verifier));
  return btoa(String.fromCharCode(...new Uint8Array(buffer))).replace(/\//g, "_").replace(/\+/g, "-").replace(/=/g, "");
}
async function pkceChallenge(length) {
  if (!length)
    length = 43;
  if (length < 43 || length > 128)
    throw `Expected a length between 43 and 128. Received ${length}.`;
  let verifier = await generateVerifier(length), challenge = await generateChallenge(verifier);
  return {
    code_verifier: verifier,
    code_challenge: challenge
  };
}

// node_modules/@modelcontextprotocol/sdk/dist/esm/shared/auth.js
var SafeUrlSchema = url().superRefine((val, ctx) => {
  if (!URL.canParse(val))
    return ctx.addIssue({
      code: ZodIssueCode.custom,
      message: "URL must be parseable",
      fatal: !0
    }), NEVER;
}).refine((url) => {
  let u = new URL(url);
  return u.protocol !== "javascript:" && u.protocol !== "data:" && u.protocol !== "vbscript:";
}, { message: "URL cannot use javascript:, data:, or vbscript: scheme" }), OAuthProtectedResourceMetadataSchema = looseObject({
  resource: string2().url(),
  authorization_servers: array(SafeUrlSchema).optional(),
  jwks_uri: string2().url().optional(),
  scopes_supported: array(string2()).optional(),
  bearer_methods_supported: array(string2()).optional(),
  resource_signing_alg_values_supported: array(string2()).optional(),
  resource_name: string2().optional(),
  resource_documentation: string2().optional(),
  resource_policy_uri: string2().url().optional(),
  resource_tos_uri: string2().url().optional(),
  tls_client_certificate_bound_access_tokens: boolean2().optional(),
  authorization_details_types_supported: array(string2()).optional(),
  dpop_signing_alg_values_supported: array(string2()).optional(),
  dpop_bound_access_tokens_required: boolean2().optional()
}), OAuthMetadataSchema = looseObject({
  issuer: string2(),
  authorization_endpoint: SafeUrlSchema,
  token_endpoint: SafeUrlSchema,
  registration_endpoint: SafeUrlSchema.optional(),
  scopes_supported: array(string2()).optional(),
  response_types_supported: array(string2()),
  response_modes_supported: array(string2()).optional(),
  grant_types_supported: array(string2()).optional(),
  token_endpoint_auth_methods_supported: array(string2()).optional(),
  token_endpoint_auth_signing_alg_values_supported: array(string2()).optional(),
  service_documentation: SafeUrlSchema.optional(),
  revocation_endpoint: SafeUrlSchema.optional(),
  revocation_endpoint_auth_methods_supported: array(string2()).optional(),
  revocation_endpoint_auth_signing_alg_values_supported: array(string2()).optional(),
  introspection_endpoint: string2().optional(),
  introspection_endpoint_auth_methods_supported: array(string2()).optional(),
  introspection_endpoint_auth_signing_alg_values_supported: array(string2()).optional(),
  code_challenge_methods_supported: array(string2()).optional(),
  client_id_metadata_document_supported: boolean2().optional()
}), OpenIdProviderMetadataSchema = looseObject({
  issuer: string2(),
  authorization_endpoint: SafeUrlSchema,
  token_endpoint: SafeUrlSchema,
  userinfo_endpoint: SafeUrlSchema.optional(),
  jwks_uri: SafeUrlSchema,
  registration_endpoint: SafeUrlSchema.optional(),
  scopes_supported: array(string2()).optional(),
  response_types_supported: array(string2()),
  response_modes_supported: array(string2()).optional(),
  grant_types_supported: array(string2()).optional(),
  acr_values_supported: array(string2()).optional(),
  subject_types_supported: array(string2()),
  id_token_signing_alg_values_supported: array(string2()),
  id_token_encryption_alg_values_supported: array(string2()).optional(),
  id_token_encryption_enc_values_supported: array(string2()).optional(),
  userinfo_signing_alg_values_supported: array(string2()).optional(),
  userinfo_encryption_alg_values_supported: array(string2()).optional(),
  userinfo_encryption_enc_values_supported: array(string2()).optional(),
  request_object_signing_alg_values_supported: array(string2()).optional(),
  request_object_encryption_alg_values_supported: array(string2()).optional(),
  request_object_encryption_enc_values_supported: array(string2()).optional(),
  token_endpoint_auth_methods_supported: array(string2()).optional(),
  token_endpoint_auth_signing_alg_values_supported: array(string2()).optional(),
  display_values_supported: array(string2()).optional(),
  claim_types_supported: array(string2()).optional(),
  claims_supported: array(string2()).optional(),
  service_documentation: string2().optional(),
  claims_locales_supported: array(string2()).optional(),
  ui_locales_supported: array(string2()).optional(),
  claims_parameter_supported: boolean2().optional(),
  request_parameter_supported: boolean2().optional(),
  request_uri_parameter_supported: boolean2().optional(),
  require_request_uri_registration: boolean2().optional(),
  op_policy_uri: SafeUrlSchema.optional(),
  op_tos_uri: SafeUrlSchema.optional(),
  client_id_metadata_document_supported: boolean2().optional()
}), OpenIdProviderDiscoveryMetadataSchema = object2({
  ...OpenIdProviderMetadataSchema.shape,
  ...OAuthMetadataSchema.pick({
    code_challenge_methods_supported: !0
  }).shape
}), OAuthTokensSchema = object2({
  access_token: string2(),
  id_token: string2().optional(),
  token_type: string2(),
  expires_in: number3().optional(),
  scope: string2().optional(),
  refresh_token: string2().optional()
}).strip(), OAuthErrorResponseSchema = object2({
  error: string2(),
  error_description: string2().optional(),
  error_uri: string2().optional()
}), OptionalSafeUrlSchema = SafeUrlSchema.optional().or(literal("").transform(() => {
  return;
})), OAuthClientMetadataSchema = object2({
  redirect_uris: array(SafeUrlSchema),
  token_endpoint_auth_method: string2().optional(),
  grant_types: array(string2()).optional(),
  response_types: array(string2()).optional(),
  client_name: string2().optional(),
  client_uri: SafeUrlSchema.optional(),
  logo_uri: OptionalSafeUrlSchema,
  scope: string2().optional(),
  contacts: array(string2()).optional(),
  tos_uri: OptionalSafeUrlSchema,
  policy_uri: string2().optional(),
  jwks_uri: SafeUrlSchema.optional(),
  jwks: any().optional(),
  software_id: string2().optional(),
  software_version: string2().optional(),
  software_statement: string2().optional()
}).strip(), OAuthClientInformationSchema = object2({
  client_id: string2(),
  client_secret: string2().optional(),
  client_id_issued_at: number2().optional(),
  client_secret_expires_at: number2().optional()
}).strip(), OAuthClientInformationFullSchema = OAuthClientMetadataSchema.merge(OAuthClientInformationSchema), OAuthClientRegistrationErrorSchema = object2({
  error: string2(),
  error_description: string2().optional()
}).strip(), OAuthTokenRevocationRequestSchema = object2({
  token: string2(),
  token_type_hint: string2().optional()
}).strip();

// node_modules/@modelcontextprotocol/sdk/dist/esm/shared/auth-utils.js
function resourceUrlFromServerUrl(url) {
  let resourceURL = typeof url === "string" ? new URL(url) : new URL(url.href);
  return resourceURL.hash = "", resourceURL;
}
function checkResourceAllowed({ requestedResource, configuredResource }) {
  let requested = typeof requestedResource === "string" ? new URL(requestedResource) : new URL(requestedResource.href), configured = typeof configuredResource === "string" ? new URL(configuredResource) : new URL(configuredResource.href);
  if (requested.origin !== configured.origin)
    return !1;
  if (requested.pathname.length < configured.pathname.length)
    return !1;
  let requestedPath = requested.pathname.endsWith("/") ? requested.pathname : requested.pathname + "/", configuredPath = configured.pathname.endsWith("/") ? configured.pathname : configured.pathname + "/";
  return requestedPath.startsWith(configuredPath);
}

// node_modules/@modelcontextprotocol/sdk/dist/esm/server/auth/errors.js
class OAuthError extends Error {
  constructor(message, errorUri) {
    super(message);
    this.errorUri = errorUri, this.name = this.constructor.name;
  }
  toResponseObject() {
    let response = {
      error: this.errorCode,
      error_description: this.message
    };
    if (this.errorUri)
      response.error_uri = this.errorUri;
    return response;
  }
  get errorCode() {
    return this.constructor.errorCode;
  }
}

class InvalidRequestError extends OAuthError {
}
InvalidRequestError.errorCode = "invalid_request";

class InvalidClientError extends OAuthError {
}
InvalidClientError.errorCode = "invalid_client";

class InvalidGrantError extends OAuthError {
}
InvalidGrantError.errorCode = "invalid_grant";

class UnauthorizedClientError extends OAuthError {
}
UnauthorizedClientError.errorCode = "unauthorized_client";

class UnsupportedGrantTypeError extends OAuthError {
}
UnsupportedGrantTypeError.errorCode = "unsupported_grant_type";

class InvalidScopeError extends OAuthError {
}
InvalidScopeError.errorCode = "invalid_scope";

class AccessDeniedError extends OAuthError {
}
AccessDeniedError.errorCode = "access_denied";

class ServerError extends OAuthError {
}
ServerError.errorCode = "server_error";

class TemporarilyUnavailableError extends OAuthError {
}
TemporarilyUnavailableError.errorCode = "temporarily_unavailable";

class UnsupportedResponseTypeError extends OAuthError {
}
UnsupportedResponseTypeError.errorCode = "unsupported_response_type";

class UnsupportedTokenTypeError extends OAuthError {
}
UnsupportedTokenTypeError.errorCode = "unsupported_token_type";

class InvalidTokenError extends OAuthError {
}
InvalidTokenError.errorCode = "invalid_token";

class MethodNotAllowedError extends OAuthError {
}
MethodNotAllowedError.errorCode = "method_not_allowed";

class TooManyRequestsError extends OAuthError {
}
TooManyRequestsError.errorCode = "too_many_requests";

class InvalidClientMetadataError extends OAuthError {
}
InvalidClientMetadataError.errorCode = "invalid_client_metadata";

class InsufficientScopeError extends OAuthError {
}
InsufficientScopeError.errorCode = "insufficient_scope";

class InvalidTargetError extends OAuthError {
}
InvalidTargetError.errorCode = "invalid_target";
var OAUTH_ERRORS = {
  [InvalidRequestError.errorCode]: InvalidRequestError,
  [InvalidClientError.errorCode]: InvalidClientError,
  [InvalidGrantError.errorCode]: InvalidGrantError,
  [UnauthorizedClientError.errorCode]: UnauthorizedClientError,
  [UnsupportedGrantTypeError.errorCode]: UnsupportedGrantTypeError,
  [InvalidScopeError.errorCode]: InvalidScopeError,
  [AccessDeniedError.errorCode]: AccessDeniedError,
  [ServerError.errorCode]: ServerError,
  [TemporarilyUnavailableError.errorCode]: TemporarilyUnavailableError,
  [UnsupportedResponseTypeError.errorCode]: UnsupportedResponseTypeError,
  [UnsupportedTokenTypeError.errorCode]: UnsupportedTokenTypeError,
  [InvalidTokenError.errorCode]: InvalidTokenError,
  [MethodNotAllowedError.errorCode]: MethodNotAllowedError,
  [TooManyRequestsError.errorCode]: TooManyRequestsError,
  [InvalidClientMetadataError.errorCode]: InvalidClientMetadataError,
  [InsufficientScopeError.errorCode]: InsufficientScopeError,
  [InvalidTargetError.errorCode]: InvalidTargetError
};

// node_modules/@modelcontextprotocol/sdk/dist/esm/client/auth.js
class UnauthorizedError extends Error {
  constructor(message) {
    super(message ?? "Unauthorized");
  }
}
function isClientAuthMethod(method) {
  return ["client_secret_basic", "client_secret_post", "none"].includes(method);
}
var AUTHORIZATION_CODE_RESPONSE_TYPE = "code", AUTHORIZATION_CODE_CHALLENGE_METHOD = "S256";
function selectClientAuthMethod(clientInformation, supportedMethods) {
  let hasClientSecret = clientInformation.client_secret !== void 0;
  if ("token_endpoint_auth_method" in clientInformation && clientInformation.token_endpoint_auth_method && isClientAuthMethod(clientInformation.token_endpoint_auth_method) && (supportedMethods.length === 0 || supportedMethods.includes(clientInformation.token_endpoint_auth_method)))
    return clientInformation.token_endpoint_auth_method;
  if (supportedMethods.length === 0)
    return hasClientSecret ? "client_secret_basic" : "none";
  if (hasClientSecret && supportedMethods.includes("client_secret_basic"))
    return "client_secret_basic";
  if (hasClientSecret && supportedMethods.includes("client_secret_post"))
    return "client_secret_post";
  if (supportedMethods.includes("none"))
    return "none";
  return hasClientSecret ? "client_secret_post" : "none";
}
function applyClientAuthentication(method, clientInformation, headers, params) {
  let { client_id, client_secret } = clientInformation;
  switch (method) {
    case "client_secret_basic":
      applyBasicAuth(client_id, client_secret, headers);
      return;
    case "client_secret_post":
      applyPostAuth(client_id, client_secret, params);
      return;
    case "none":
      applyPublicAuth(client_id, params);
      return;
    default:
      throw Error(`Unsupported client authentication method: ${method}`);
  }
}
function applyBasicAuth(clientId, clientSecret, headers) {
  if (!clientSecret)
    throw Error("client_secret_basic authentication requires a client_secret");
  let credentials = btoa(`${clientId}:${clientSecret}`);
  headers.set("Authorization", `Basic ${credentials}`);
}
function applyPostAuth(clientId, clientSecret, params) {
  if (params.set("client_id", clientId), clientSecret)
    params.set("client_secret", clientSecret);
}
function applyPublicAuth(clientId, params) {
  params.set("client_id", clientId);
}
async function parseErrorResponse(input) {
  let statusCode = input instanceof Response ? input.status : void 0, body = input instanceof Response ? await input.text() : input;
  try {
    let result = OAuthErrorResponseSchema.parse(JSON.parse(body)), { error, error_description, error_uri } = result;
    return new (OAUTH_ERRORS[error] || ServerError)(error_description || "", error_uri);
  } catch (error) {
    let errorMessage = `${statusCode ? `HTTP ${statusCode}: ` : ""}Invalid OAuth error response: ${error}. Raw body: ${body}`;
    return new ServerError(errorMessage);
  }
}
async function auth(provider, options) {
  try {
    return await authInternal(provider, options);
  } catch (error) {
    if (error instanceof InvalidClientError || error instanceof UnauthorizedClientError)
      return await provider.invalidateCredentials?.("all"), await authInternal(provider, options);
    else if (error instanceof InvalidGrantError)
      return await provider.invalidateCredentials?.("tokens"), await authInternal(provider, options);
    throw error;
  }
}
async function authInternal(provider, { serverUrl, authorizationCode, scope, resourceMetadataUrl, fetchFn }) {
  let cachedState = await provider.discoveryState?.(), resourceMetadata, authorizationServerUrl, metadata, effectiveResourceMetadataUrl = resourceMetadataUrl;
  if (!effectiveResourceMetadataUrl && cachedState?.resourceMetadataUrl)
    effectiveResourceMetadataUrl = new URL(cachedState.resourceMetadataUrl);
  if (cachedState?.authorizationServerUrl) {
    if (authorizationServerUrl = cachedState.authorizationServerUrl, resourceMetadata = cachedState.resourceMetadata, metadata = cachedState.authorizationServerMetadata ?? await discoverAuthorizationServerMetadata(authorizationServerUrl, { fetchFn }), !resourceMetadata)
      try {
        resourceMetadata = await discoverOAuthProtectedResourceMetadata(serverUrl, { resourceMetadataUrl: effectiveResourceMetadataUrl }, fetchFn);
      } catch {}
    if (metadata !== cachedState.authorizationServerMetadata || resourceMetadata !== cachedState.resourceMetadata)
      await provider.saveDiscoveryState?.({
        authorizationServerUrl: String(authorizationServerUrl),
        resourceMetadataUrl: effectiveResourceMetadataUrl?.toString(),
        resourceMetadata,
        authorizationServerMetadata: metadata
      });
  } else {
    let serverInfo = await discoverOAuthServerInfo(serverUrl, { resourceMetadataUrl: effectiveResourceMetadataUrl, fetchFn });
    authorizationServerUrl = serverInfo.authorizationServerUrl, metadata = serverInfo.authorizationServerMetadata, resourceMetadata = serverInfo.resourceMetadata, await provider.saveDiscoveryState?.({
      authorizationServerUrl: String(authorizationServerUrl),
      resourceMetadataUrl: effectiveResourceMetadataUrl?.toString(),
      resourceMetadata,
      authorizationServerMetadata: metadata
    });
  }
  let resource = await selectResourceURL(serverUrl, provider, resourceMetadata), resolvedScope = scope || resourceMetadata?.scopes_supported?.join(" ") || provider.clientMetadata.scope, clientInformation = await Promise.resolve(provider.clientInformation());
  if (!clientInformation) {
    if (authorizationCode !== void 0)
      throw Error("Existing OAuth client information is required when exchanging an authorization code");
    let supportsUrlBasedClientId = metadata?.client_id_metadata_document_supported === !0, clientMetadataUrl = provider.clientMetadataUrl;
    if (clientMetadataUrl && !isHttpsUrl(clientMetadataUrl))
      throw new InvalidClientMetadataError(`clientMetadataUrl must be a valid HTTPS URL with a non-root pathname, got: ${clientMetadataUrl}`);
    if (supportsUrlBasedClientId && clientMetadataUrl)
      clientInformation = {
        client_id: clientMetadataUrl
      }, await provider.saveClientInformation?.(clientInformation);
    else {
      if (!provider.saveClientInformation)
        throw Error("OAuth client information must be saveable for dynamic registration");
      let fullInformation = await registerClient(authorizationServerUrl, {
        metadata,
        clientMetadata: provider.clientMetadata,
        scope: resolvedScope,
        fetchFn
      });
      await provider.saveClientInformation(fullInformation), clientInformation = fullInformation;
    }
  }
  let nonInteractiveFlow = !provider.redirectUrl;
  if (authorizationCode !== void 0 || nonInteractiveFlow) {
    let tokens = await fetchToken(provider, authorizationServerUrl, {
      metadata,
      resource,
      authorizationCode,
      fetchFn
    });
    return await provider.saveTokens(tokens), "AUTHORIZED";
  }
  let tokens = await provider.tokens();
  if (tokens?.refresh_token)
    try {
      let newTokens = await refreshAuthorization(authorizationServerUrl, {
        metadata,
        clientInformation,
        refreshToken: tokens.refresh_token,
        resource,
        addClientAuthentication: provider.addClientAuthentication,
        fetchFn
      });
      return await provider.saveTokens(newTokens), "AUTHORIZED";
    } catch (error) {
      if (!(error instanceof OAuthError) || error instanceof ServerError)
        ;
      else
        throw error;
    }
  let state = provider.state ? await provider.state() : void 0, { authorizationUrl, codeVerifier } = await startAuthorization(authorizationServerUrl, {
    metadata,
    clientInformation,
    state,
    redirectUrl: provider.redirectUrl,
    scope: resolvedScope,
    resource
  });
  return await provider.saveCodeVerifier(codeVerifier), await provider.redirectToAuthorization(authorizationUrl), "REDIRECT";
}
function isHttpsUrl(value) {
  if (!value)
    return !1;
  try {
    let url = new URL(value);
    return url.protocol === "https:" && url.pathname !== "/";
  } catch {
    return !1;
  }
}
async function selectResourceURL(serverUrl, provider, resourceMetadata) {
  let defaultResource = resourceUrlFromServerUrl(serverUrl);
  if (provider.validateResourceURL)
    return await provider.validateResourceURL(defaultResource, resourceMetadata?.resource);
  if (!resourceMetadata)
    return;
  if (!checkResourceAllowed({ requestedResource: defaultResource, configuredResource: resourceMetadata.resource }))
    throw Error(`Protected resource ${resourceMetadata.resource} does not match expected ${defaultResource} (or origin)`);
  return new URL(resourceMetadata.resource);
}
function extractWWWAuthenticateParams(res) {
  let authenticateHeader = res.headers.get("WWW-Authenticate");
  if (!authenticateHeader)
    return {};
  let [type, scheme] = authenticateHeader.split(" ");
  if (type.toLowerCase() !== "bearer" || !scheme)
    return {};
  let resourceMetadataMatch = extractFieldFromWwwAuth(res, "resource_metadata") || void 0, resourceMetadataUrl;
  if (resourceMetadataMatch)
    try {
      resourceMetadataUrl = new URL(resourceMetadataMatch);
    } catch {}
  let scope = extractFieldFromWwwAuth(res, "scope") || void 0, error = extractFieldFromWwwAuth(res, "error") || void 0;
  return {
    resourceMetadataUrl,
    scope,
    error
  };
}
function extractFieldFromWwwAuth(response, fieldName) {
  let wwwAuthHeader = response.headers.get("WWW-Authenticate");
  if (!wwwAuthHeader)
    return null;
  let pattern = new RegExp(`${fieldName}=(?:"([^"]+)"|([^\\s,]+))`), match = wwwAuthHeader.match(pattern);
  if (match)
    return match[1] || match[2];
  return null;
}
async function discoverOAuthProtectedResourceMetadata(serverUrl, opts, fetchFn = fetch) {
  let response = await discoverMetadataWithFallback(serverUrl, "oauth-protected-resource", fetchFn, {
    protocolVersion: opts?.protocolVersion,
    metadataUrl: opts?.resourceMetadataUrl
  });
  if (!response || response.status === 404)
    throw await response?.body?.cancel(), Error("Resource server does not implement OAuth 2.0 Protected Resource Metadata.");
  if (!response.ok)
    throw await response.body?.cancel(), Error(`HTTP ${response.status} trying to load well-known OAuth protected resource metadata.`);
  return OAuthProtectedResourceMetadataSchema.parse(await response.json());
}
async function fetchWithCorsRetry(url, headers, fetchFn = fetch) {
  try {
    return await fetchFn(url, { headers });
  } catch (error) {
    if (error instanceof TypeError)
      if (headers)
        return fetchWithCorsRetry(url, void 0, fetchFn);
      else
        return;
    throw error;
  }
}
function buildWellKnownPath(wellKnownPrefix, pathname = "", options = {}) {
  if (pathname.endsWith("/"))
    pathname = pathname.slice(0, -1);
  return options.prependPathname ? `${pathname}/.well-known/${wellKnownPrefix}` : `/.well-known/${wellKnownPrefix}${pathname}`;
}
async function tryMetadataDiscovery(url, protocolVersion, fetchFn = fetch) {
  return await fetchWithCorsRetry(url, {
    "MCP-Protocol-Version": protocolVersion
  }, fetchFn);
}
function shouldAttemptFallback(response, pathname) {
  return !response || response.status >= 400 && response.status < 500 && pathname !== "/";
}
async function discoverMetadataWithFallback(serverUrl, wellKnownType, fetchFn, opts) {
  let issuer = new URL(serverUrl), protocolVersion = opts?.protocolVersion ?? LATEST_PROTOCOL_VERSION, url;
  if (opts?.metadataUrl)
    url = new URL(opts.metadataUrl);
  else {
    let wellKnownPath = buildWellKnownPath(wellKnownType, issuer.pathname);
    url = new URL(wellKnownPath, opts?.metadataServerUrl ?? issuer), url.search = issuer.search;
  }
  let response = await tryMetadataDiscovery(url, protocolVersion, fetchFn);
  if (!opts?.metadataUrl && shouldAttemptFallback(response, issuer.pathname)) {
    let rootUrl = new URL(`/.well-known/${wellKnownType}`, issuer);
    response = await tryMetadataDiscovery(rootUrl, protocolVersion, fetchFn);
  }
  return response;
}
function buildDiscoveryUrls(authorizationServerUrl) {
  let url = typeof authorizationServerUrl === "string" ? new URL(authorizationServerUrl) : authorizationServerUrl, hasPath = url.pathname !== "/", urlsToTry = [];
  if (!hasPath)
    return urlsToTry.push({
      url: new URL("/.well-known/oauth-authorization-server", url.origin),
      type: "oauth"
    }), urlsToTry.push({
      url: new URL("/.well-known/openid-configuration", url.origin),
      type: "oidc"
    }), urlsToTry;
  let pathname = url.pathname;
  if (pathname.endsWith("/"))
    pathname = pathname.slice(0, -1);
  return urlsToTry.push({
    url: new URL(`/.well-known/oauth-authorization-server${pathname}`, url.origin),
    type: "oauth"
  }), urlsToTry.push({
    url: new URL(`/.well-known/openid-configuration${pathname}`, url.origin),
    type: "oidc"
  }), urlsToTry.push({
    url: new URL(`${pathname}/.well-known/openid-configuration`, url.origin),
    type: "oidc"
  }), urlsToTry;
}
async function discoverAuthorizationServerMetadata(authorizationServerUrl, { fetchFn = fetch, protocolVersion = LATEST_PROTOCOL_VERSION } = {}) {
  let headers = {
    "MCP-Protocol-Version": protocolVersion,
    Accept: "application/json"
  }, urlsToTry = buildDiscoveryUrls(authorizationServerUrl);
  for (let { url: endpointUrl, type } of urlsToTry) {
    let response = await fetchWithCorsRetry(endpointUrl, headers, fetchFn);
    if (!response)
      continue;
    if (!response.ok) {
      if (await response.body?.cancel(), response.status >= 400 && response.status < 500)
        continue;
      throw Error(`HTTP ${response.status} trying to load ${type === "oauth" ? "OAuth" : "OpenID provider"} metadata from ${endpointUrl}`);
    }
    if (type === "oauth")
      return OAuthMetadataSchema.parse(await response.json());
    else
      return OpenIdProviderDiscoveryMetadataSchema.parse(await response.json());
  }
  return;
}
async function discoverOAuthServerInfo(serverUrl, opts) {
  let resourceMetadata, authorizationServerUrl;
  try {
    if (resourceMetadata = await discoverOAuthProtectedResourceMetadata(serverUrl, { resourceMetadataUrl: opts?.resourceMetadataUrl }, opts?.fetchFn), resourceMetadata.authorization_servers && resourceMetadata.authorization_servers.length > 0)
      authorizationServerUrl = resourceMetadata.authorization_servers[0];
  } catch {}
  if (!authorizationServerUrl)
    authorizationServerUrl = String(new URL("/", serverUrl));
  let authorizationServerMetadata = await discoverAuthorizationServerMetadata(authorizationServerUrl, { fetchFn: opts?.fetchFn });
  return {
    authorizationServerUrl,
    authorizationServerMetadata,
    resourceMetadata
  };
}
async function startAuthorization(authorizationServerUrl, { metadata, clientInformation, redirectUrl, scope, state, resource }) {
  let authorizationUrl;
  if (metadata) {
    if (authorizationUrl = new URL(metadata.authorization_endpoint), !metadata.response_types_supported.includes(AUTHORIZATION_CODE_RESPONSE_TYPE))
      throw Error(`Incompatible auth server: does not support response type ${AUTHORIZATION_CODE_RESPONSE_TYPE}`);
    if (metadata.code_challenge_methods_supported && !metadata.code_challenge_methods_supported.includes(AUTHORIZATION_CODE_CHALLENGE_METHOD))
      throw Error(`Incompatible auth server: does not support code challenge method ${AUTHORIZATION_CODE_CHALLENGE_METHOD}`);
  } else
    authorizationUrl = new URL("/authorize", authorizationServerUrl);
  let challenge = await pkceChallenge(), { code_verifier: codeVerifier, code_challenge: codeChallenge } = challenge;
  if (authorizationUrl.searchParams.set("response_type", AUTHORIZATION_CODE_RESPONSE_TYPE), authorizationUrl.searchParams.set("client_id", clientInformation.client_id), authorizationUrl.searchParams.set("code_challenge", codeChallenge), authorizationUrl.searchParams.set("code_challenge_method", AUTHORIZATION_CODE_CHALLENGE_METHOD), authorizationUrl.searchParams.set("redirect_uri", String(redirectUrl)), state)
    authorizationUrl.searchParams.set("state", state);
  if (scope)
    authorizationUrl.searchParams.set("scope", scope);
  if (scope?.includes("offline_access"))
    authorizationUrl.searchParams.append("prompt", "consent");
  if (resource)
    authorizationUrl.searchParams.set("resource", resource.href);
  return { authorizationUrl, codeVerifier };
}
function prepareAuthorizationCodeRequest(authorizationCode, codeVerifier, redirectUri) {
  return new URLSearchParams({
    grant_type: "authorization_code",
    code: authorizationCode,
    code_verifier: codeVerifier,
    redirect_uri: String(redirectUri)
  });
}
async function executeTokenRequest(authorizationServerUrl, { metadata, tokenRequestParams, clientInformation, addClientAuthentication, resource, fetchFn }) {
  let tokenUrl = metadata?.token_endpoint ? new URL(metadata.token_endpoint) : new URL("/token", authorizationServerUrl), headers = new Headers({
    "Content-Type": "application/x-www-form-urlencoded",
    Accept: "application/json"
  });
  if (resource)
    tokenRequestParams.set("resource", resource.href);
  if (addClientAuthentication)
    await addClientAuthentication(headers, tokenRequestParams, tokenUrl, metadata);
  else if (clientInformation) {
    let supportedMethods = metadata?.token_endpoint_auth_methods_supported ?? [], authMethod = selectClientAuthMethod(clientInformation, supportedMethods);
    applyClientAuthentication(authMethod, clientInformation, headers, tokenRequestParams);
  }
  let response = await (fetchFn ?? fetch)(tokenUrl, {
    method: "POST",
    headers,
    body: tokenRequestParams
  });
  if (!response.ok)
    throw await parseErrorResponse(response);
  return OAuthTokensSchema.parse(await response.json());
}
async function refreshAuthorization(authorizationServerUrl, { metadata, clientInformation, refreshToken, resource, addClientAuthentication, fetchFn }) {
  let tokenRequestParams = new URLSearchParams({
    grant_type: "refresh_token",
    refresh_token: refreshToken
  }), tokens = await executeTokenRequest(authorizationServerUrl, {
    metadata,
    tokenRequestParams,
    clientInformation,
    addClientAuthentication,
    resource,
    fetchFn
  });
  return { refresh_token: refreshToken, ...tokens };
}
async function fetchToken(provider, authorizationServerUrl, { metadata, resource, authorizationCode, fetchFn } = {}) {
  let scope = provider.clientMetadata.scope, tokenRequestParams;
  if (provider.prepareTokenRequest)
    tokenRequestParams = await provider.prepareTokenRequest(scope);
  if (!tokenRequestParams) {
    if (!authorizationCode)
      throw Error("Either provider.prepareTokenRequest() or authorizationCode is required");
    if (!provider.redirectUrl)
      throw Error("redirectUrl is required for authorization_code flow");
    let codeVerifier = await provider.codeVerifier();
    tokenRequestParams = prepareAuthorizationCodeRequest(authorizationCode, codeVerifier, provider.redirectUrl);
  }
  let clientInformation = await provider.clientInformation();
  return executeTokenRequest(authorizationServerUrl, {
    metadata,
    tokenRequestParams,
    clientInformation: clientInformation ?? void 0,
    addClientAuthentication: provider.addClientAuthentication,
    resource,
    fetchFn
  });
}
async function registerClient(authorizationServerUrl, { metadata, clientMetadata, scope, fetchFn }) {
  let registrationUrl;
  if (metadata) {
    if (!metadata.registration_endpoint)
      throw Error("Incompatible auth server: does not support dynamic client registration");
    registrationUrl = new URL(metadata.registration_endpoint);
  } else
    registrationUrl = new URL("/register", authorizationServerUrl);
  let response = await (fetchFn ?? fetch)(registrationUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      ...clientMetadata,
      ...scope !== void 0 ? { scope } : {}
    })
  });
  if (!response.ok)
    throw await parseErrorResponse(response);
  return OAuthClientInformationFullSchema.parse(await response.json());
}

// node_modules/eventsource-parser/dist/index.js
class ParseError extends Error {
  constructor(message, options) {
    super(message), this.name = "ParseError", this.type = options.type, this.field = options.field, this.value = options.value, this.line = options.line;
  }
}
function noop(_arg) {}
function createParser(callbacks) {
  if (typeof callbacks == "function")
    throw TypeError("`callbacks` must be an object, got a function instead. Did you mean `{onEvent: fn}`?");
  let { onEvent = noop, onError = noop, onRetry = noop, onComment } = callbacks, incompleteLine = "", isFirstChunk = !0, id, data = "", eventType = "";
  function feed(newChunk) {
    let chunk = isFirstChunk ? newChunk.replace(/^\xEF\xBB\xBF/, "") : newChunk, [complete, incomplete] = splitLines(`${incompleteLine}${chunk}`);
    for (let line of complete)
      parseLine(line);
    incompleteLine = incomplete, isFirstChunk = !1;
  }
  function parseLine(line) {
    if (line === "") {
      dispatchEvent();
      return;
    }
    if (line.startsWith(":")) {
      onComment && onComment(line.slice(line.startsWith(": ") ? 2 : 1));
      return;
    }
    let fieldSeparatorIndex = line.indexOf(":");
    if (fieldSeparatorIndex !== -1) {
      let field = line.slice(0, fieldSeparatorIndex), offset = line[fieldSeparatorIndex + 1] === " " ? 2 : 1, value = line.slice(fieldSeparatorIndex + offset);
      processField(field, value, line);
      return;
    }
    processField(line, "", line);
  }
  function processField(field, value, line) {
    switch (field) {
      case "event":
        eventType = value;
        break;
      case "data":
        data = `${data}${value}
`;
        break;
      case "id":
        id = value.includes("\x00") ? void 0 : value;
        break;
      case "retry":
        /^\d+$/.test(value) ? onRetry(parseInt(value, 10)) : onError(new ParseError(`Invalid \`retry\` value: "${value}"`, {
          type: "invalid-retry",
          value,
          line
        }));
        break;
      default:
        onError(new ParseError(`Unknown field "${field.length > 20 ? `${field.slice(0, 20)}\u2026` : field}"`, { type: "unknown-field", field, value, line }));
        break;
    }
  }
  function dispatchEvent() {
    data.length > 0 && onEvent({
      id,
      event: eventType || void 0,
      data: data.endsWith(`
`) ? data.slice(0, -1) : data
    }), id = void 0, data = "", eventType = "";
  }
  function reset(options = {}) {
    incompleteLine && options.consume && parseLine(incompleteLine), isFirstChunk = !0, id = void 0, data = "", eventType = "", incompleteLine = "";
  }
  return { feed, reset };
}
function splitLines(chunk) {
  let lines = [], incompleteLine = "", searchIndex = 0;
  for (;searchIndex < chunk.length; ) {
    let crIndex = chunk.indexOf("\r", searchIndex), lfIndex = chunk.indexOf(`
`, searchIndex), lineEnd = -1;
    if (crIndex !== -1 && lfIndex !== -1 ? lineEnd = Math.min(crIndex, lfIndex) : crIndex !== -1 ? crIndex === chunk.length - 1 ? lineEnd = -1 : lineEnd = crIndex : lfIndex !== -1 && (lineEnd = lfIndex), lineEnd === -1) {
      incompleteLine = chunk.slice(searchIndex);
      break;
    } else {
      let line = chunk.slice(searchIndex, lineEnd);
      lines.push(line), searchIndex = lineEnd + 1, chunk[searchIndex - 1] === "\r" && chunk[searchIndex] === `
` && searchIndex++;
    }
  }
  return [lines, incompleteLine];
}

// node_modules/eventsource-parser/dist/stream.js
class EventSourceParserStream extends TransformStream {
  constructor({ onError, onRetry, onComment } = {}) {
    let parser;
    super({
      start(controller) {
        parser = createParser({
          onEvent: (event) => {
            controller.enqueue(event);
          },
          onError(error) {
            onError === "terminate" ? controller.error(error) : typeof onError == "function" && onError(error);
          },
          onRetry,
          onComment
        });
      },
      transform(chunk) {
        parser.feed(chunk);
      }
    });
  }
}

// node_modules/@modelcontextprotocol/sdk/dist/esm/client/streamableHttp.js
var DEFAULT_STREAMABLE_HTTP_RECONNECTION_OPTIONS = {
  initialReconnectionDelay: 1000,
  maxReconnectionDelay: 30000,
  reconnectionDelayGrowFactor: 1.5,
  maxRetries: 2
};

class StreamableHTTPError extends Error {
  constructor(code, message) {
    super(`Streamable HTTP error: ${message}`);
    this.code = code;
  }
}

class StreamableHTTPClientTransport {
  constructor(url, opts) {
    this._hasCompletedAuthFlow = !1, this._url = url, this._resourceMetadataUrl = void 0, this._scope = void 0, this._requestInit = opts?.requestInit, this._authProvider = opts?.authProvider, this._fetch = opts?.fetch, this._fetchWithInit = createFetchWithInit(opts?.fetch, opts?.requestInit), this._sessionId = opts?.sessionId, this._reconnectionOptions = opts?.reconnectionOptions ?? DEFAULT_STREAMABLE_HTTP_RECONNECTION_OPTIONS;
  }
  async _authThenStart() {
    if (!this._authProvider)
      throw new UnauthorizedError("No auth provider");
    let result;
    try {
      result = await auth(this._authProvider, {
        serverUrl: this._url,
        resourceMetadataUrl: this._resourceMetadataUrl,
        scope: this._scope,
        fetchFn: this._fetchWithInit
      });
    } catch (error) {
      throw this.onerror?.(error), error;
    }
    if (result !== "AUTHORIZED")
      throw new UnauthorizedError;
    return await this._startOrAuthSse({ resumptionToken: void 0 });
  }
  async _commonHeaders() {
    let headers = {};
    if (this._authProvider) {
      let tokens = await this._authProvider.tokens();
      if (tokens)
        headers.Authorization = `Bearer ${tokens.access_token}`;
    }
    if (this._sessionId)
      headers["mcp-session-id"] = this._sessionId;
    if (this._protocolVersion)
      headers["mcp-protocol-version"] = this._protocolVersion;
    let extraHeaders = normalizeHeaders(this._requestInit?.headers);
    return new Headers({
      ...headers,
      ...extraHeaders
    });
  }
  async _startOrAuthSse(options) {
    let { resumptionToken } = options;
    try {
      let headers = await this._commonHeaders();
      if (headers.set("Accept", "text/event-stream"), resumptionToken)
        headers.set("last-event-id", resumptionToken);
      let response = await (this._fetch ?? fetch)(this._url, {
        method: "GET",
        headers,
        signal: this._abortController?.signal
      });
      if (!response.ok) {
        if (await response.body?.cancel(), response.status === 401 && this._authProvider)
          return await this._authThenStart();
        if (response.status === 405)
          return;
        throw new StreamableHTTPError(response.status, `Failed to open SSE stream: ${response.statusText}`);
      }
      this._handleSseStream(response.body, options, !0);
    } catch (error) {
      throw this.onerror?.(error), error;
    }
  }
  _getNextReconnectionDelay(attempt) {
    if (this._serverRetryMs !== void 0)
      return this._serverRetryMs;
    let initialDelay = this._reconnectionOptions.initialReconnectionDelay, growFactor = this._reconnectionOptions.reconnectionDelayGrowFactor, maxDelay = this._reconnectionOptions.maxReconnectionDelay;
    return Math.min(initialDelay * Math.pow(growFactor, attempt), maxDelay);
  }
  _scheduleReconnection(options, attemptCount = 0) {
    let maxRetries = this._reconnectionOptions.maxRetries;
    if (attemptCount >= maxRetries) {
      this.onerror?.(Error(`Maximum reconnection attempts (${maxRetries}) exceeded.`));
      return;
    }
    let delay = this._getNextReconnectionDelay(attemptCount);
    this._reconnectionTimeout = setTimeout(() => {
      this._startOrAuthSse(options).catch((error) => {
        this.onerror?.(Error(`Failed to reconnect SSE stream: ${error instanceof Error ? error.message : String(error)}`)), this._scheduleReconnection(options, attemptCount + 1);
      });
    }, delay);
  }
  _handleSseStream(stream, options, isReconnectable) {
    if (!stream)
      return;
    let { onresumptiontoken, replayMessageId } = options, lastEventId, hasPrimingEvent = !1, receivedResponse = !1;
    (async () => {
      try {
        let reader = stream.pipeThrough(new TextDecoderStream).pipeThrough(new EventSourceParserStream({
          onRetry: (retryMs) => {
            this._serverRetryMs = retryMs;
          }
        })).getReader();
        while (!0) {
          let { value: event, done } = await reader.read();
          if (done)
            break;
          if (event.id)
            lastEventId = event.id, hasPrimingEvent = !0, onresumptiontoken?.(event.id);
          if (!event.data)
            continue;
          if (!event.event || event.event === "message")
            try {
              let message = JSONRPCMessageSchema.parse(JSON.parse(event.data));
              if (isJSONRPCResultResponse(message)) {
                if (receivedResponse = !0, replayMessageId !== void 0)
                  message.id = replayMessageId;
              }
              this.onmessage?.(message);
            } catch (error) {
              this.onerror?.(error);
            }
        }
        if ((isReconnectable || hasPrimingEvent) && !receivedResponse && this._abortController && !this._abortController.signal.aborted)
          this._scheduleReconnection({
            resumptionToken: lastEventId,
            onresumptiontoken,
            replayMessageId
          }, 0);
      } catch (error) {
        if (this.onerror?.(Error(`SSE stream disconnected: ${error}`)), (isReconnectable || hasPrimingEvent) && !receivedResponse && this._abortController && !this._abortController.signal.aborted)
          try {
            this._scheduleReconnection({
              resumptionToken: lastEventId,
              onresumptiontoken,
              replayMessageId
            }, 0);
          } catch (error) {
            this.onerror?.(Error(`Failed to reconnect: ${error instanceof Error ? error.message : String(error)}`));
          }
      }
    })();
  }
  async start() {
    if (this._abortController)
      throw Error("StreamableHTTPClientTransport already started! If using Client class, note that connect() calls start() automatically.");
    this._abortController = new AbortController;
  }
  async finishAuth(authorizationCode) {
    if (!this._authProvider)
      throw new UnauthorizedError("No auth provider");
    if (await auth(this._authProvider, {
      serverUrl: this._url,
      authorizationCode,
      resourceMetadataUrl: this._resourceMetadataUrl,
      scope: this._scope,
      fetchFn: this._fetchWithInit
    }) !== "AUTHORIZED")
      throw new UnauthorizedError("Failed to authorize");
  }
  async close() {
    if (this._reconnectionTimeout)
      clearTimeout(this._reconnectionTimeout), this._reconnectionTimeout = void 0;
    this._abortController?.abort(), this.onclose?.();
  }
  async send(message, options) {
    try {
      let { resumptionToken, onresumptiontoken } = options || {};
      if (resumptionToken) {
        this._startOrAuthSse({ resumptionToken, replayMessageId: isJSONRPCRequest(message) ? message.id : void 0 }).catch((err) => this.onerror?.(err));
        return;
      }
      let headers = await this._commonHeaders();
      headers.set("content-type", "application/json"), headers.set("accept", "application/json, text/event-stream");
      let init = {
        ...this._requestInit,
        method: "POST",
        headers,
        body: JSON.stringify(message),
        signal: this._abortController?.signal
      }, response = await (this._fetch ?? fetch)(this._url, init), sessionId = response.headers.get("mcp-session-id");
      if (sessionId)
        this._sessionId = sessionId;
      if (!response.ok) {
        let text = await response.text().catch(() => null);
        if (response.status === 401 && this._authProvider) {
          if (this._hasCompletedAuthFlow)
            throw new StreamableHTTPError(401, "Server returned 401 after successful authentication");
          let { resourceMetadataUrl, scope } = extractWWWAuthenticateParams(response);
          if (this._resourceMetadataUrl = resourceMetadataUrl, this._scope = scope, await auth(this._authProvider, {
            serverUrl: this._url,
            resourceMetadataUrl: this._resourceMetadataUrl,
            scope: this._scope,
            fetchFn: this._fetchWithInit
          }) !== "AUTHORIZED")
            throw new UnauthorizedError;
          return this._hasCompletedAuthFlow = !0, this.send(message);
        }
        if (response.status === 403 && this._authProvider) {
          let { resourceMetadataUrl, scope, error } = extractWWWAuthenticateParams(response);
          if (error === "insufficient_scope") {
            let wwwAuthHeader = response.headers.get("WWW-Authenticate");
            if (this._lastUpscopingHeader === wwwAuthHeader)
              throw new StreamableHTTPError(403, "Server returned 403 after trying upscoping");
            if (scope)
              this._scope = scope;
            if (resourceMetadataUrl)
              this._resourceMetadataUrl = resourceMetadataUrl;
            if (this._lastUpscopingHeader = wwwAuthHeader ?? void 0, await auth(this._authProvider, {
              serverUrl: this._url,
              resourceMetadataUrl: this._resourceMetadataUrl,
              scope: this._scope,
              fetchFn: this._fetch
            }) !== "AUTHORIZED")
              throw new UnauthorizedError;
            return this.send(message);
          }
        }
        throw new StreamableHTTPError(response.status, `Error POSTing to endpoint: ${text}`);
      }
      if (this._hasCompletedAuthFlow = !1, this._lastUpscopingHeader = void 0, response.status === 202) {
        if (await response.body?.cancel(), isInitializedNotification(message))
          this._startOrAuthSse({ resumptionToken: void 0 }).catch((err) => this.onerror?.(err));
        return;
      }
      let hasRequests = (Array.isArray(message) ? message : [message]).filter((msg) => ("method" in msg) && ("id" in msg) && msg.id !== void 0).length > 0, contentType = response.headers.get("content-type"), responseMediaType = mediaTypeEssence(contentType);
      if (hasRequests)
        if (responseMediaType === "text/event-stream")
          this._handleSseStream(response.body, { onresumptiontoken }, !1);
        else if (responseMediaType === "application/json") {
          let data = await response.json(), responseMessages = Array.isArray(data) ? data.map((msg) => JSONRPCMessageSchema.parse(msg)) : [JSONRPCMessageSchema.parse(data)];
          for (let msg of responseMessages)
            this.onmessage?.(msg);
        } else
          throw await response.body?.cancel(), new StreamableHTTPError(-1, `Unexpected content type: ${contentType}`);
      else
        await response.body?.cancel();
    } catch (error) {
      throw this.onerror?.(error), error;
    }
  }
  get sessionId() {
    return this._sessionId;
  }
  async terminateSession() {
    if (!this._sessionId)
      return;
    try {
      let headers = await this._commonHeaders(), init = {
        ...this._requestInit,
        method: "DELETE",
        headers,
        signal: this._abortController?.signal
      }, response = await (this._fetch ?? fetch)(this._url, init);
      if (await response.body?.cancel(), !response.ok && response.status !== 405)
        throw new StreamableHTTPError(response.status, `Failed to terminate session: ${response.statusText}`);
      this._sessionId = void 0;
    } catch (error) {
      throw this.onerror?.(error), error;
    }
  }
  setProtocolVersion(version) {
    this._protocolVersion = version;
  }
  get protocolVersion() {
    return this._protocolVersion;
  }
  async resumeStream(lastEventId, options) {
    await this._startOrAuthSse({
      resumptionToken: lastEventId,
      onresumptiontoken: options?.onresumptiontoken
    });
  }
}

// stream-transport.ts
var SESSION_NOT_FOUND_RE = /session not found/i;
function isSessionNotFoundError(error) {
  if (!error)
    return !1;
  let message = error instanceof Error ? error.message : String(error);
  if (!SESSION_NOT_FOUND_RE.test(message))
    return !1;
  let code = error.code;
  if (typeof code === "number")
    return code === -32000 || code === 400 || code === 404 || code === 410;
  return !0;
}
function headersToObject(headers) {
  let result = {};
  return new Headers(headers).forEach((value, key) => {
    result[key] = value;
  }), result;
}
function headersFromIncoming(headers) {
  let result = /* @__PURE__ */ new Headers;
  for (let [key, value] of Object.entries(headers))
    if (Array.isArray(value))
      for (let item of value)
        result.append(key, item);
    else if (value !== void 0)
      result.set(key, String(value));
  return result;
}
function bodyToNodeBody(body) {
  if (body == null)
    return;
  if (typeof body === "string")
    return body;
  if (body instanceof URLSearchParams)
    return body.toString();
  if (body instanceof ArrayBuffer)
    return Buffer.from(body);
  if (ArrayBuffer.isView(body))
    return Buffer.from(body.buffer, body.byteOffset, body.byteLength);
  throw Error(`Unsupported MCP upstream fetch body type: ${Object.prototype.toString.call(body)}`);
}
function signalReasonToError(signal) {
  let reason = signal?.reason;
  if (reason instanceof Error)
    return reason;
  return Error(reason === void 0 ? "Request aborted" : String(reason));
}
function createNodeHttpFetch() {
  return async (url, init) => {
    let target = url instanceof URL ? url : new URL(url), request = target.protocol === "https:" ? httpsRequest : httpRequest;
    if (target.protocol !== "http:" && target.protocol !== "https:")
      throw Error(`Unsupported MCP upstream fetch protocol: ${target.protocol}`);
    let body = bodyToNodeBody(init?.body), signal = init?.signal;
    return await new Promise((resolve, reject) => {
      let response, req = request(target, {
        method: init?.method ?? "GET",
        headers: headersToObject(init?.headers)
      }, (res) => {
        response = res, res.on("close", cleanup), resolve(new Response(Readable.toWeb(res), {
          status: res.statusCode ?? 500,
          statusText: res.statusMessage,
          headers: headersFromIncoming(res.headers)
        }));
      });
      function cleanup() {
        signal?.removeEventListener("abort", abort);
      }
      function abort() {
        let error = signalReasonToError(signal);
        cleanup(), req.destroy(error), response?.destroy(error), reject(error);
      }
      if (req.on("error", (error) => {
        cleanup(), reject(error);
      }), signal?.aborted) {
        abort();
        return;
      }
      if (signal?.addEventListener("abort", abort, { once: !0 }), body !== void 0)
        req.write(body);
      req.end();
    });
  };
}

class StreamTransportImpl {
  _options;
  _queue;
  _connectPromise;
  _transport;
  _protocolVersion;
  _closed;
  _closeNotified;
  sessionId;
  onmessage;
  onerror;
  onclose;
  constructor(options) {
    this._options = options, this._queue = [], this._connectPromise = null, this._transport = null, this._protocolVersion = null, this._closed = !1, this._closeNotified = !1, this.sessionId = void 0;
  }
  async start() {
    await this._ensureConnected();
  }
  async send(message, options) {
    if (this._closed)
      throw Error("Transport is closed");
    if (this._transport) {
      await this._sendDirect(message, options);
      return;
    }
    await this._enqueue(message, options);
  }
  async close() {
    if (this._closed)
      return;
    if (this._closed = !0, this._transport)
      await this._transport.close(), this._transport = null;
    this._rejectQueue(Error("Transport closed")), this._emitClose();
  }
  setProtocolVersion(version) {
    if (this._protocolVersion = version, this._transport?.setProtocolVersion)
      this._transport.setProtocolVersion(version);
  }
  async resetTransport(reason) {
    let warn = this._options.warn, message = reason instanceof Error ? reason.message : String(reason);
    if (warn)
      warn(`MCP stream session invalid; reconnecting. ${message}`);
    let transport = this._transport;
    if (this._transport = null, this.sessionId = void 0, transport)
      try {
        await transport.close();
      } catch (error) {
        let closeMessage = error instanceof Error ? error.message : String(error);
        if (warn)
          warn(`Failed to close stale MCP transport: ${closeMessage}`);
      }
  }
  async _sendDirect(message, options) {
    let retried = !1;
    while (!0)
      try {
        if (!this._transport)
          await this._ensureConnected();
        await this._transport.send(message, options), this.sessionId = this._transport.sessionId;
        return;
      } catch (error) {
        let err = error instanceof Error ? error : Error(String(error));
        if (!retried && isSessionNotFoundError(err)) {
          retried = !0, await this.resetTransport(err);
          continue;
        }
        if (this.onerror)
          this.onerror(err);
        throw err;
      }
  }
  async _enqueue(message, options) {
    let limit = this._options.queueLimit;
    if (limit > 0 && this._queue.length >= limit)
      throw Error(`MCP proxy queue limit (${limit}) reached before stream connection`);
    await new Promise((resolve, reject) => {
      let entry = {
        message,
        options,
        resolve,
        reject,
        timeout: null
      };
      if (this._options.queueWaitTimeoutMs > 0)
        entry.timeout = setTimeout(() => {
          this._removeQueueEntry(entry), reject(Error(`Upstream tool call timed out before it was sent after ${this._options.queueWaitTimeoutMs}ms`));
        }, this._options.queueWaitTimeoutMs);
      this._queue.push(entry), this._ensureConnected().catch((error) => {
        this._removeQueueEntry(entry), reject(error);
      });
    });
  }
  async _ensureConnected() {
    if (this._closed)
      throw Error("Transport is closed");
    if (this._transport)
      return;
    if (this._connectPromise)
      return this._connectPromise;
    return this._connectPromise = pRetry(async () => {
      let { url: targetUrl, note } = this._options;
      if (note)
        note(`Connecting to MCP stream ${targetUrl}`);
      let transport = new StreamableHTTPClientTransport(targetUrl, {
        fetch: createNodeHttpFetch(),
        requestInit: { headers: this._options.requestHeaders }
      });
      if (transport.onmessage = (message, extra) => {
        if (this.onmessage)
          this.onmessage(message, extra);
      }, transport.onerror = (error) => {
        if (this.onerror)
          this.onerror(error);
      }, transport.onclose = () => {
        this._transport = null, this.sessionId = void 0, this._emitClose();
      }, this._protocolVersion && transport.setProtocolVersion)
        transport.setProtocolVersion(this._protocolVersion);
      await transport.start(), this._transport = transport, this.sessionId = transport.sessionId, this._closeNotified = !1, await this._flushQueue();
    }, {
      retries: Math.max(this._options.retryAttempts - 1, 0),
      minTimeout: this._options.retryBaseDelayMs,
      onFailedAttempt: (error) => {
        if (this._options.warn)
          this._options.warn(`MCP stream connection attempt failed (${error.attemptNumber}/${error.retriesLeft + error.attemptNumber}): ${error.message}`);
      }
    }).finally(() => {
      this._connectPromise = null;
    }), this._connectPromise;
  }
  async _flushQueue() {
    if (!this._transport || this._queue.length === 0)
      return;
    let queued = this._queue.slice();
    this._queue.length = 0;
    for (let entry of queued) {
      if (entry.timeout)
        clearTimeout(entry.timeout), entry.timeout = null;
      try {
        await this._sendDirect(entry.message, entry.options), entry.resolve();
      } catch (error) {
        entry.reject(error);
      }
    }
  }
  _removeQueueEntry(entry) {
    let index = this._queue.indexOf(entry);
    if (index >= 0)
      this._queue.splice(index, 1);
    if (entry.timeout)
      clearTimeout(entry.timeout), entry.timeout = null;
  }
  _rejectQueue(error) {
    let queued = this._queue.slice();
    this._queue.length = 0;
    for (let entry of queued) {
      if (entry.timeout)
        clearTimeout(entry.timeout), entry.timeout = null;
      entry.reject(error);
    }
  }
  _emitClose() {
    if (this._closeNotified)
      return;
    if (this._closeNotified = !0, this.onclose)
      this.onclose();
  }
}
function createStreamTransport(options) {
  return new StreamTransportImpl(options);
}

// upstream.ts
import { AsyncLocalStorage } from "async_hooks";

// node_modules/@modelcontextprotocol/sdk/dist/esm/experimental/tasks/client.js
class ExperimentalClientTasks {
  constructor(_client) {
    this._client = _client;
  }
  async* callToolStream(params, resultSchema = CallToolResultSchema, options) {
    let clientInternal = this._client, optionsWithTask = {
      ...options,
      task: options?.task ?? (clientInternal.isToolTask(params.name) ? {} : void 0)
    }, stream = clientInternal.requestStream({ method: "tools/call", params }, resultSchema, optionsWithTask), validator = clientInternal.getToolOutputValidator(params.name);
    for await (let message of stream) {
      if (message.type === "result" && validator) {
        let result = message.result;
        if (!result.structuredContent && !result.isError) {
          yield {
            type: "error",
            error: new McpError(ErrorCode.InvalidRequest, `Tool ${params.name} has an output schema but did not return structured content`)
          };
          return;
        }
        if (result.structuredContent)
          try {
            let validationResult = validator(result.structuredContent);
            if (!validationResult.valid) {
              yield {
                type: "error",
                error: new McpError(ErrorCode.InvalidParams, `Structured content does not match the tool's output schema: ${validationResult.errorMessage}`)
              };
              return;
            }
          } catch (error) {
            if (error instanceof McpError) {
              yield { type: "error", error };
              return;
            }
            yield {
              type: "error",
              error: new McpError(ErrorCode.InvalidParams, `Failed to validate structured content: ${error instanceof Error ? error.message : String(error)}`)
            };
            return;
          }
      }
      yield message;
    }
  }
  async getTask(taskId, options) {
    return this._client.getTask({ taskId }, options);
  }
  async getTaskResult(taskId, resultSchema, options) {
    return this._client.getTaskResult({ taskId }, resultSchema, options);
  }
  async listTasks(cursor, options) {
    return this._client.listTasks(cursor ? { cursor } : void 0, options);
  }
  async cancelTask(taskId, options) {
    return this._client.cancelTask({ taskId }, options);
  }
  requestStream(request, resultSchema, options) {
    return this._client.requestStream(request, resultSchema, options);
  }
}

// node_modules/@modelcontextprotocol/sdk/dist/esm/client/index.js
function applyElicitationDefaults(schema, data) {
  if (!schema || data === null || typeof data !== "object")
    return;
  if (schema.type === "object" && schema.properties && typeof schema.properties === "object") {
    let obj = data, props = schema.properties;
    for (let key of Object.keys(props)) {
      let propSchema = props[key];
      if (obj[key] === void 0 && Object.prototype.hasOwnProperty.call(propSchema, "default"))
        obj[key] = propSchema.default;
      if (obj[key] !== void 0)
        applyElicitationDefaults(propSchema, obj[key]);
    }
  }
  if (Array.isArray(schema.anyOf)) {
    for (let sub of schema.anyOf)
      if (typeof sub !== "boolean")
        applyElicitationDefaults(sub, data);
  }
  if (Array.isArray(schema.oneOf)) {
    for (let sub of schema.oneOf)
      if (typeof sub !== "boolean")
        applyElicitationDefaults(sub, data);
  }
}
function getSupportedElicitationModes(capabilities) {
  if (!capabilities)
    return { supportsFormMode: !1, supportsUrlMode: !1 };
  let hasFormCapability = capabilities.form !== void 0, hasUrlCapability = capabilities.url !== void 0;
  return { supportsFormMode: hasFormCapability || !hasFormCapability && !hasUrlCapability, supportsUrlMode: hasUrlCapability };
}

class Client extends Protocol {
  constructor(_clientInfo, options) {
    super(options);
    if (this._clientInfo = _clientInfo, this._cachedToolOutputValidators = /* @__PURE__ */ new Map, this._cachedKnownTaskTools = /* @__PURE__ */ new Set, this._cachedRequiredTaskTools = /* @__PURE__ */ new Set, this._listChangedDebounceTimers = /* @__PURE__ */ new Map, this._capabilities = options?.capabilities ?? {}, this._jsonSchemaValidator = options?.jsonSchemaValidator ?? new AjvJsonSchemaValidator, options?.listChanged)
      this._pendingListChangedConfig = options.listChanged;
  }
  _setupListChangedHandlers(config) {
    if (config.tools && this._serverCapabilities?.tools?.listChanged)
      this._setupListChangedHandler("tools", ToolListChangedNotificationSchema, config.tools, async () => (await this.listTools()).tools);
    if (config.prompts && this._serverCapabilities?.prompts?.listChanged)
      this._setupListChangedHandler("prompts", PromptListChangedNotificationSchema, config.prompts, async () => (await this.listPrompts()).prompts);
    if (config.resources && this._serverCapabilities?.resources?.listChanged)
      this._setupListChangedHandler("resources", ResourceListChangedNotificationSchema, config.resources, async () => (await this.listResources()).resources);
  }
  get experimental() {
    if (!this._experimental)
      this._experimental = {
        tasks: new ExperimentalClientTasks(this)
      };
    return this._experimental;
  }
  registerCapabilities(capabilities) {
    if (this.transport)
      throw Error("Cannot register capabilities after connecting to transport");
    this._capabilities = mergeCapabilities(this._capabilities, capabilities);
  }
  setRequestHandler(requestSchema, handler) {
    let methodSchema = getObjectShape(requestSchema)?.method;
    if (!methodSchema)
      throw Error("Schema is missing a method literal");
    let methodValue = getLiteralValue(methodSchema);
    if (typeof methodValue !== "string")
      throw Error("Schema method literal must be a string");
    let method = methodValue;
    if (method === "elicitation/create") {
      let wrappedHandler = async (request, extra) => {
        let validatedRequest = safeParse2(ElicitRequestSchema, request);
        if (!validatedRequest.success) {
          let errorMessage = validatedRequest.error instanceof Error ? validatedRequest.error.message : String(validatedRequest.error);
          throw new McpError(ErrorCode.InvalidParams, `Invalid elicitation request: ${errorMessage}`);
        }
        let { params } = validatedRequest.data;
        params.mode = params.mode ?? "form";
        let { supportsFormMode, supportsUrlMode } = getSupportedElicitationModes(this._capabilities.elicitation);
        if (params.mode === "form" && !supportsFormMode)
          throw new McpError(ErrorCode.InvalidParams, "Client does not support form-mode elicitation requests");
        if (params.mode === "url" && !supportsUrlMode)
          throw new McpError(ErrorCode.InvalidParams, "Client does not support URL-mode elicitation requests");
        let result = await Promise.resolve(handler(request, extra));
        if (params.task) {
          let taskValidationResult = safeParse2(CreateTaskResultSchema, result);
          if (!taskValidationResult.success) {
            let errorMessage = taskValidationResult.error instanceof Error ? taskValidationResult.error.message : String(taskValidationResult.error);
            throw new McpError(ErrorCode.InvalidParams, `Invalid task creation result: ${errorMessage}`);
          }
          return taskValidationResult.data;
        }
        let validationResult = safeParse2(ElicitResultSchema, result);
        if (!validationResult.success) {
          let errorMessage = validationResult.error instanceof Error ? validationResult.error.message : String(validationResult.error);
          throw new McpError(ErrorCode.InvalidParams, `Invalid elicitation result: ${errorMessage}`);
        }
        let validatedResult = validationResult.data, requestedSchema = params.mode === "form" ? params.requestedSchema : void 0;
        if (params.mode === "form" && validatedResult.action === "accept" && validatedResult.content && requestedSchema) {
          if (this._capabilities.elicitation?.form?.applyDefaults)
            try {
              applyElicitationDefaults(requestedSchema, validatedResult.content);
            } catch {}
        }
        return validatedResult;
      };
      return super.setRequestHandler(requestSchema, wrappedHandler);
    }
    if (method === "sampling/createMessage") {
      let wrappedHandler = async (request, extra) => {
        let validatedRequest = safeParse2(CreateMessageRequestSchema, request);
        if (!validatedRequest.success) {
          let errorMessage = validatedRequest.error instanceof Error ? validatedRequest.error.message : String(validatedRequest.error);
          throw new McpError(ErrorCode.InvalidParams, `Invalid sampling request: ${errorMessage}`);
        }
        let { params } = validatedRequest.data, result = await Promise.resolve(handler(request, extra));
        if (params.task) {
          let taskValidationResult = safeParse2(CreateTaskResultSchema, result);
          if (!taskValidationResult.success) {
            let errorMessage = taskValidationResult.error instanceof Error ? taskValidationResult.error.message : String(taskValidationResult.error);
            throw new McpError(ErrorCode.InvalidParams, `Invalid task creation result: ${errorMessage}`);
          }
          return taskValidationResult.data;
        }
        let resultSchema = params.tools || params.toolChoice ? CreateMessageResultWithToolsSchema : CreateMessageResultSchema, validationResult = safeParse2(resultSchema, result);
        if (!validationResult.success) {
          let errorMessage = validationResult.error instanceof Error ? validationResult.error.message : String(validationResult.error);
          throw new McpError(ErrorCode.InvalidParams, `Invalid sampling result: ${errorMessage}`);
        }
        return validationResult.data;
      };
      return super.setRequestHandler(requestSchema, wrappedHandler);
    }
    return super.setRequestHandler(requestSchema, handler);
  }
  assertCapability(capability, method) {
    if (!this._serverCapabilities?.[capability])
      throw Error(`Server does not support ${capability} (required for ${method})`);
  }
  async connect(transport, options) {
    if (await super.connect(transport), transport.sessionId !== void 0)
      return;
    try {
      let result = await this.request({
        method: "initialize",
        params: {
          protocolVersion: LATEST_PROTOCOL_VERSION,
          capabilities: this._capabilities,
          clientInfo: this._clientInfo
        }
      }, InitializeResultSchema, options);
      if (result === void 0)
        throw Error(`Server sent invalid initialize result: ${result}`);
      if (!SUPPORTED_PROTOCOL_VERSIONS.includes(result.protocolVersion))
        throw Error(`Server's protocol version is not supported: ${result.protocolVersion}`);
      if (this._serverCapabilities = result.capabilities, this._serverVersion = result.serverInfo, transport.setProtocolVersion)
        transport.setProtocolVersion(result.protocolVersion);
      if (this._instructions = result.instructions, await this.notification({
        method: "notifications/initialized"
      }), this._pendingListChangedConfig)
        this._setupListChangedHandlers(this._pendingListChangedConfig), this._pendingListChangedConfig = void 0;
    } catch (error) {
      throw this.close(), error;
    }
  }
  getServerCapabilities() {
    return this._serverCapabilities;
  }
  getServerVersion() {
    return this._serverVersion;
  }
  getInstructions() {
    return this._instructions;
  }
  assertCapabilityForMethod(method) {
    switch (method) {
      case "logging/setLevel":
        if (!this._serverCapabilities?.logging)
          throw Error(`Server does not support logging (required for ${method})`);
        break;
      case "prompts/get":
      case "prompts/list":
        if (!this._serverCapabilities?.prompts)
          throw Error(`Server does not support prompts (required for ${method})`);
        break;
      case "resources/list":
      case "resources/templates/list":
      case "resources/read":
      case "resources/subscribe":
      case "resources/unsubscribe":
        if (!this._serverCapabilities?.resources)
          throw Error(`Server does not support resources (required for ${method})`);
        if (method === "resources/subscribe" && !this._serverCapabilities.resources.subscribe)
          throw Error(`Server does not support resource subscriptions (required for ${method})`);
        break;
      case "tools/call":
      case "tools/list":
        if (!this._serverCapabilities?.tools)
          throw Error(`Server does not support tools (required for ${method})`);
        break;
      case "completion/complete":
        if (!this._serverCapabilities?.completions)
          throw Error(`Server does not support completions (required for ${method})`);
        break;
      case "initialize":
        break;
      case "ping":
        break;
    }
  }
  assertNotificationCapability(method) {
    switch (method) {
      case "notifications/roots/list_changed":
        if (!this._capabilities.roots?.listChanged)
          throw Error(`Client does not support roots list changed notifications (required for ${method})`);
        break;
      case "notifications/initialized":
        break;
      case "notifications/cancelled":
        break;
      case "notifications/progress":
        break;
    }
  }
  assertRequestHandlerCapability(method) {
    if (!this._capabilities)
      return;
    switch (method) {
      case "sampling/createMessage":
        if (!this._capabilities.sampling)
          throw Error(`Client does not support sampling capability (required for ${method})`);
        break;
      case "elicitation/create":
        if (!this._capabilities.elicitation)
          throw Error(`Client does not support elicitation capability (required for ${method})`);
        break;
      case "roots/list":
        if (!this._capabilities.roots)
          throw Error(`Client does not support roots capability (required for ${method})`);
        break;
      case "tasks/get":
      case "tasks/list":
      case "tasks/result":
      case "tasks/cancel":
        if (!this._capabilities.tasks)
          throw Error(`Client does not support tasks capability (required for ${method})`);
        break;
      case "ping":
        break;
    }
  }
  assertTaskCapability(method) {
    assertToolsCallTaskCapability(this._serverCapabilities?.tasks?.requests, method, "Server");
  }
  assertTaskHandlerCapability(method) {
    if (!this._capabilities)
      return;
    assertClientRequestTaskCapability(this._capabilities.tasks?.requests, method, "Client");
  }
  async ping(options) {
    return this.request({ method: "ping" }, EmptyResultSchema, options);
  }
  async complete(params, options) {
    return this.request({ method: "completion/complete", params }, CompleteResultSchema, options);
  }
  async setLoggingLevel(level, options) {
    return this.request({ method: "logging/setLevel", params: { level } }, EmptyResultSchema, options);
  }
  async getPrompt(params, options) {
    return this.request({ method: "prompts/get", params }, GetPromptResultSchema, options);
  }
  async listPrompts(params, options) {
    return this.request({ method: "prompts/list", params }, ListPromptsResultSchema, options);
  }
  async listResources(params, options) {
    return this.request({ method: "resources/list", params }, ListResourcesResultSchema, options);
  }
  async listResourceTemplates(params, options) {
    return this.request({ method: "resources/templates/list", params }, ListResourceTemplatesResultSchema, options);
  }
  async readResource(params, options) {
    return this.request({ method: "resources/read", params }, ReadResourceResultSchema, options);
  }
  async subscribeResource(params, options) {
    return this.request({ method: "resources/subscribe", params }, EmptyResultSchema, options);
  }
  async unsubscribeResource(params, options) {
    return this.request({ method: "resources/unsubscribe", params }, EmptyResultSchema, options);
  }
  async callTool(params, resultSchema = CallToolResultSchema, options) {
    if (this.isToolTaskRequired(params.name))
      throw new McpError(ErrorCode.InvalidRequest, `Tool "${params.name}" requires task-based execution. Use client.experimental.tasks.callToolStream() instead.`);
    let result = await this.request({ method: "tools/call", params }, resultSchema, options), validator = this.getToolOutputValidator(params.name);
    if (validator) {
      if (!result.structuredContent && !result.isError)
        throw new McpError(ErrorCode.InvalidRequest, `Tool ${params.name} has an output schema but did not return structured content`);
      if (result.structuredContent)
        try {
          let validationResult = validator(result.structuredContent);
          if (!validationResult.valid)
            throw new McpError(ErrorCode.InvalidParams, `Structured content does not match the tool's output schema: ${validationResult.errorMessage}`);
        } catch (error) {
          if (error instanceof McpError)
            throw error;
          throw new McpError(ErrorCode.InvalidParams, `Failed to validate structured content: ${error instanceof Error ? error.message : String(error)}`);
        }
    }
    return result;
  }
  isToolTask(toolName) {
    if (!this._serverCapabilities?.tasks?.requests?.tools?.call)
      return !1;
    return this._cachedKnownTaskTools.has(toolName);
  }
  isToolTaskRequired(toolName) {
    return this._cachedRequiredTaskTools.has(toolName);
  }
  cacheToolMetadata(tools) {
    this._cachedToolOutputValidators.clear(), this._cachedKnownTaskTools.clear(), this._cachedRequiredTaskTools.clear();
    for (let tool of tools) {
      if (tool.outputSchema) {
        let toolValidator = this._jsonSchemaValidator.getValidator(tool.outputSchema);
        this._cachedToolOutputValidators.set(tool.name, toolValidator);
      }
      let taskSupport = tool.execution?.taskSupport;
      if (taskSupport === "required" || taskSupport === "optional")
        this._cachedKnownTaskTools.add(tool.name);
      if (taskSupport === "required")
        this._cachedRequiredTaskTools.add(tool.name);
    }
  }
  getToolOutputValidator(toolName) {
    return this._cachedToolOutputValidators.get(toolName);
  }
  async listTools(params, options) {
    let result = await this.request({ method: "tools/list", params }, ListToolsResultSchema, options);
    return this.cacheToolMetadata(result.tools), result;
  }
  _setupListChangedHandler(listType, notificationSchema, options, fetcher) {
    let parseResult = ListChangedOptionsBaseSchema.safeParse(options);
    if (!parseResult.success)
      throw Error(`Invalid ${listType} listChanged options: ${parseResult.error.message}`);
    if (typeof options.onChanged !== "function")
      throw Error(`Invalid ${listType} listChanged options: onChanged must be a function`);
    let { autoRefresh, debounceMs } = parseResult.data, { onChanged } = options, refresh = async () => {
      if (!autoRefresh) {
        onChanged(null, null);
        return;
      }
      try {
        let items = await fetcher();
        onChanged(null, items);
      } catch (e) {
        let error = e instanceof Error ? e : Error(String(e));
        onChanged(error, null);
      }
    }, handler = () => {
      if (debounceMs) {
        let existingTimer = this._listChangedDebounceTimers.get(listType);
        if (existingTimer)
          clearTimeout(existingTimer);
        let timer = setTimeout(refresh, debounceMs);
        this._listChangedDebounceTimers.set(listType, timer);
      } else
        refresh();
    };
    this.setNotificationHandler(notificationSchema, handler);
  }
  async sendRootsListChanged() {
    return this.notification({ method: "notifications/roots/list_changed" });
  }
}

// project-path.ts
function createProjectPathManager({
  projectPath,
  defaultProjectPathKey = "projectPath",
  forceInject = !1
}) {
  let projectPathKey = null, hasSeenToolsList = !1, hasProjectPathTools = !1, toolProjectPathKeyByName = /* @__PURE__ */ new Map;
  function normalizeProjectPathArgs(args, desiredKey) {
    if (!desiredKey)
      return;
    let hasSnake = Object.prototype.hasOwnProperty.call(args, "project_path"), hasCamel = Object.prototype.hasOwnProperty.call(args, "projectPath"), hasRoot = Object.prototype.hasOwnProperty.call(args, "rootFolder");
    if (desiredKey === "projectPath") {
      if (hasSnake)
        delete args.project_path;
      if (hasRoot)
        delete args.rootFolder;
      args.projectPath = projectPath;
      return;
    }
    if (desiredKey === "project_path") {
      if (hasCamel)
        delete args.projectPath;
      if (hasRoot)
        delete args.rootFolder;
      args.project_path = projectPath;
      return;
    }
    if (desiredKey === "rootFolder") {
      if (hasSnake)
        delete args.project_path;
      if (hasCamel)
        delete args.projectPath;
      args.rootFolder = projectPath;
    }
  }
  function shouldInjectProjectPath(toolName) {
    if (forceInject)
      return !0;
    if (!hasSeenToolsList)
      return !0;
    if (!hasProjectPathTools)
      return !1;
    if (!toolName)
      return !0;
    return toolProjectPathKeyByName.has(toolName);
  }
  function chooseProjectPathKey(toolName) {
    if (toolName) {
      let key = toolProjectPathKeyByName.get(toolName);
      if (key)
        return key;
    }
    return projectPathKey || defaultProjectPathKey;
  }
  function injectProjectPathArgs(toolName, args) {
    if (!args || typeof args !== "object")
      return;
    if (shouldInjectProjectPath(toolName))
      normalizeProjectPathArgs(args, chooseProjectPathKey(toolName));
  }
  function updateProjectPathKeys(tools) {
    if (!Array.isArray(tools))
      return;
    let hasSnake = !1, hasCamel = !1, hasRoot = !1;
    toolProjectPathKeyByName.clear();
    for (let tool of tools) {
      let props = tool?.inputSchema?.properties;
      if (!props || typeof props !== "object")
        continue;
      if (Object.prototype.hasOwnProperty.call(props, "project_path")) {
        if (hasSnake = !0, typeof tool.name === "string")
          toolProjectPathKeyByName.set(tool.name, "project_path");
        continue;
      }
      if (Object.prototype.hasOwnProperty.call(props, "projectPath")) {
        if (hasCamel = !0, typeof tool.name === "string")
          toolProjectPathKeyByName.set(tool.name, "projectPath");
        continue;
      }
      if (Object.prototype.hasOwnProperty.call(props, "rootFolder")) {
        if (hasRoot = !0, typeof tool.name === "string")
          toolProjectPathKeyByName.set(tool.name, "rootFolder");
      }
    }
    if (hasSeenToolsList = !0, hasProjectPathTools = toolProjectPathKeyByName.size > 0, hasSnake)
      projectPathKey = "project_path";
    else if (hasCamel)
      projectPathKey = "projectPath";
    else if (hasRoot)
      projectPathKey = "rootFolder";
    else
      projectPathKey = null;
  }
  function stripProjectPathFromTools(tools) {
    if (!Array.isArray(tools))
      return;
    for (let tool of tools) {
      let schema = tool?.inputSchema;
      if (!schema || schema.type !== "object")
        continue;
      let props = schema.properties;
      if (!props || typeof props !== "object")
        continue;
      let removedKeys = [];
      if (Object.prototype.hasOwnProperty.call(props, "project_path"))
        delete props.project_path, removedKeys.push("project_path");
      if (Object.prototype.hasOwnProperty.call(props, "projectPath"))
        delete props.projectPath, removedKeys.push("projectPath");
      if (Object.prototype.hasOwnProperty.call(props, "rootFolder"))
        delete props.rootFolder, removedKeys.push("rootFolder");
      if (removedKeys.length > 0 && Array.isArray(schema.required))
        schema.required = schema.required.filter((name) => !removedKeys.includes(name));
    }
  }
  return {
    injectProjectPathArgs,
    stripProjectPathFromTools,
    updateProjectPathKeys
  };
}

// proxy-tools/handlers/rename.ts
import { createHash } from "crypto";
import { createReadStream } from "fs";
import { readdir } from "fs/promises";
import path2 from "path";

// proxy-tools/shared.ts
import path from "path";
var nonEmptyStringSchema = string2().refine((value) => value.trim() !== "", {
  message: "must be a non-empty string"
});
function parseWithMessage(schema, value, message) {
  let parsed = schema.safeParse(value);
  if (!parsed.success)
    throw Error(message);
  return parsed.data;
}
function requireString(value, label) {
  return parseWithMessage(nonEmptyStringSchema, value, `${label} must be a non-empty string`);
}
function resolvePathInProject(projectPath, inputPath, label) {
  let rawPath = requireString(inputPath, label), absolute = path.isAbsolute(rawPath) ? path.normalize(rawPath) : path.resolve(projectPath, rawPath), relative = path.relative(projectPath, absolute);
  if (relative.startsWith("..") || path.isAbsolute(relative))
    throw Error(`${label} must be within the project root`);
  return { absolute, relative };
}
function normalizeProjectRelativePath(projectPath, filePath) {
  if (!filePath)
    return "";
  if (path.isAbsolute(filePath)) {
    let relative = path.relative(projectPath, filePath);
    if (!relative.startsWith("..") && !path.isAbsolute(relative))
      return toPosixPath(relative);
    return path.normalize(filePath);
  }
  return toPosixPath(path.normalize(filePath));
}
function toPosixPath(value) {
  return value.replace(/\\/g, "/");
}
function extractTextFromResult(result) {
  if (!result)
    return null;
  if (typeof result === "string")
    return result;
  let typedResult = result;
  if (typeof typedResult.text === "string")
    return typedResult.text;
  let content = typedResult.content;
  if (Array.isArray(content)) {
    for (let item of content)
      if (item && typeof item.text === "string")
        return item.text;
  }
  if (typeof content === "string")
    return content;
  return null;
}
function extractStructuredContent(result) {
  if (!result)
    return null;
  let typedResult = result;
  if (typedResult.structuredContent !== void 0)
    return typedResult.structuredContent;
  let text = extractTextFromResult(result);
  if (!text)
    return null;
  let trimmed = text.trim();
  if (!trimmed.startsWith("{") && !trimmed.startsWith("["))
    return null;
  try {
    return JSON.parse(trimmed);
  } catch {
    return null;
  }
}
function isRecord(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}
function coerceSearchItem(value) {
  if (typeof value === "string")
    return { filePath: value };
  if (Array.isArray(value)) {
    if (value.length === 0 || value.length > 3)
      return null;
    if (typeof value[0] !== "string")
      return null;
    let item = { filePath: value[0] };
    if (typeof value[1] === "number")
      item.startLine = value[1];
    return item;
  }
  if (isRecord(value)) {
    let filePath = typeof value.filePath === "string" ? value.filePath : null;
    if (!filePath)
      return null;
    let item = { ...value, filePath };
    if (typeof value.startLine === "number")
      item.startLine = value.startLine;
    else if (typeof value.lineNumber === "number")
      item.startLine = value.lineNumber;
    else
      delete item.startLine;
    if (typeof value.startColumn !== "number")
      delete item.startColumn;
    if (typeof value.endLine !== "number")
      delete item.endLine;
    if (typeof value.endColumn !== "number")
      delete item.endColumn;
    return delete item.lineNumber, delete item.lineText, delete item.startOffset, delete item.endOffset, item;
  }
  return null;
}
function coerceItems(value) {
  if (!Array.isArray(value))
    return null;
  let items = [];
  for (let entry of value) {
    let item = coerceSearchItem(entry);
    if (item)
      items.push(item);
  }
  return items;
}
function extractItemsFromValue(value) {
  if (!value)
    return null;
  if (Array.isArray(value))
    return coerceItems(value);
  if (!isRecord(value))
    return null;
  if (Array.isArray(value.items))
    return coerceItems(value.items);
  if (Array.isArray(value.entries))
    return coerceItems(value.entries);
  if (Array.isArray(value.results))
    return coerceItems(value.results);
  if (Array.isArray(value.files))
    return coerceItems(value.files);
  let resultsMap = extractResultsMapFromValue(value);
  if (resultsMap)
    return coerceItems(flattenResultsMap(resultsMap));
  return null;
}
function extractItems(result) {
  let structured = extractStructuredContent(result);
  return extractItemsFromValue(structured) ?? [];
}
function coerceEntries(value) {
  if (!Array.isArray(value))
    return null;
  let entries = [];
  for (let item of value) {
    if (!item)
      continue;
    if (typeof item === "string") {
      entries.push({ filePath: item });
      continue;
    }
    if (typeof item === "object")
      entries.push(item);
  }
  return entries;
}
function extractResultsMapFromValue(value) {
  if (!isRecord(value))
    return null;
  let rawResults = value.results;
  if (!isRecord(rawResults))
    return null;
  let results = {};
  for (let [key, rawEntries] of Object.entries(rawResults)) {
    let entries = coerceEntries(rawEntries);
    if (entries)
      results[key] = entries;
  }
  return results;
}
function flattenResultsMap(results) {
  let entries = [];
  for (let groupEntries of Object.values(results))
    entries.push(...groupEntries);
  return entries;
}

// proxy-tools/handlers/rename.ts
var RENAME_FILE_CHANGES_PREFIX = "IJ_PROXY_RENAME_FILE_CHANGES=";
async function handleRenameTool(args, projectPath, callUpstreamTool, readDirectoryEntries = readDirectoryEntryNames) {
  let toolArgs = args ?? {}, filePath = requireString(toolArgs.pathInProject, "pathInProject"), symbolName = requireString(toolArgs.symbolName, "symbolName"), newName = requireString(toolArgs.newName, "newName"), { relative } = resolvePathInProject(projectPath, filePath, "pathInProject"), normalizedRelative = toPosixPath2(relative), candidatePaths = await findCandidatePaths(projectPath, normalizedRelative, symbolName, callUpstreamTool), before = await fingerprintPaths(projectPath, candidatePaths), result = await callUpstreamTool("rename_refactoring", {
    pathInProject: relative,
    symbolName,
    newName
  }), after = await fingerprintPaths(projectPath, candidatePaths), changes = await collectRenameFileChanges(projectPath, normalizedRelative, symbolName, newName, before, after, readDirectoryEntries);
  return `${extractTextFromResult(result) ?? `Renamed ${symbolName} to ${newName} in ${path2.resolve(projectPath, relative)}`}
${RENAME_FILE_CHANGES_PREFIX}${JSON.stringify({ version: 1, changes })}`;
}
async function findCandidatePaths(projectPath, originalPath, symbolName, callUpstreamTool) {
  let paths = /* @__PURE__ */ new Set([originalPath]), excludedPaths = /* @__PURE__ */ new Set;
  for (let page = 0;page < MAX_SEARCH_PAGES; page++) {
    let searchResult = await callUpstreamTool("search_text", {
      q: symbolName,
      limit: MAX_SEARCH_RESULTS,
      ...excludedPaths.size > 0 ? { paths: [...excludedPaths].sort().map(exactPathExclusion) } : {}
    }), addedPath = !1;
    for (let item of extractItems(searchResult)) {
      let candidatePath = projectRelativePath(projectPath, item.filePath);
      if (!candidatePath)
        continue;
      if (paths.add(candidatePath), !excludedPaths.has(candidatePath))
        excludedPaths.add(candidatePath), addedPath = !0;
    }
    if (!hasMoreSearchResults(searchResult))
      return [...paths];
    if (!addedPath)
      throw Error("Cannot rename safely because search_text returned an incomplete page with no new project files.");
  }
  throw Error(`Cannot rename safely because search_text did not finish after ${MAX_SEARCH_PAGES} pages.`);
}
async function collectRenameFileChanges(projectPath, originalPath, symbolName, newName, before, after, readDirectoryEntries) {
  let changedPaths = [.../* @__PURE__ */ new Set([...before.keys(), ...after.keys()])].filter((filePath) => before.get(filePath) !== after.get(filePath)), renamedPath = await inferredRenamedPath(projectPath, originalPath, symbolName, newName, readDirectoryEntries), primary = renamedPath ? { kind: "MOVE", path: renamedPath, previousPath: originalPath } : { kind: "MODIFY", path: originalPath }, primaryPaths = new Set([primary.path, primary.previousPath].filter((value) => value != null)), usages = changedPaths.filter((changedPath) => !primaryPaths.has(changedPath)).sort().map((changedPath) => ({ kind: "MODIFY", path: changedPath }));
  return [primary, ...usages];
}
async function inferredRenamedPath(projectPath, originalPath, symbolName, newName, readDirectoryEntries) {
  let extension = path2.extname(originalPath);
  if (path2.basename(originalPath, extension) !== symbolName)
    return null;
  let renamedPath = toPosixPath2(path2.join(path2.dirname(originalPath), `${newName}${extension}`)), directoryEntries = await readDirectoryEntries(path2.resolve(projectPath, path2.dirname(originalPath))).catch(() => []);
  if (directoryEntries.includes(path2.basename(renamedPath)) && !directoryEntries.includes(path2.basename(originalPath)))
    return renamedPath;
  return null;
}
function hasMoreSearchResults(result) {
  let structured = extractStructuredContent(result);
  return structured != null && typeof structured === "object" && !Array.isArray(structured) && structured.more === !0;
}
function exactPathExclusion(filePath) {
  let globPath = escapeGlobPath(filePath);
  return `!{${globPath},./${globPath}}`;
}
function escapeGlobPath(filePath) {
  let result = "";
  for (let character of filePath)
    switch (character) {
      case "*":
      case "?":
      case "{":
      case "}":
      case ",":
        result += `[${character}]`;
        break;
      case "[":
        result += "[[]";
        break;
      default:
        result += character;
    }
  return result;
}
function projectRelativePath(projectPath, filePath) {
  try {
    return toPosixPath2(resolvePathInProject(projectPath, filePath, "search result path").relative);
  } catch {
    return null;
  }
}
async function fingerprintPaths(projectPath, paths) {
  let result = /* @__PURE__ */ new Map;
  for (let offset = 0;offset < paths.length; offset += FILE_HASH_CONCURRENCY) {
    let batch = paths.slice(offset, offset + FILE_HASH_CONCURRENCY), fingerprints = await Promise.all(batch.map((filePath) => fingerprintPath(path2.resolve(projectPath, filePath))));
    batch.forEach((filePath, index) => result.set(filePath, fingerprints[index]));
  }
  return result;
}
async function fingerprintPath(absolutePath) {
  try {
    return await hashFile(absolutePath);
  } catch {
    return "unreadable";
  }
}
async function hashFile(absolutePath) {
  let hash = createHash("sha256");
  for await (let chunk of createReadStream(absolutePath))
    hash.update(chunk);
  return hash.digest("hex");
}
async function readDirectoryEntryNames(directoryPath) {
  return readdir(directoryPath);
}
function toPosixPath2(value) {
  return value.replace(/\\/g, "/");
}
var FILE_HASH_CONCURRENCY = 8, MAX_SEARCH_RESULTS = 5000, MAX_SEARCH_PAGES = 1000;

// proxy-tools/container-handlers.ts
import path4 from "path";

// container-session.ts
import { readFileSync } from "fs";
import path3 from "path";
import { cwd, env } from "process";
import { fileURLToPath } from "url";
var CONTAINER_SESSION_FILE = ".container-sessions.jsonl";
function scriptDir() {
  try {
    return path3.dirname(fileURLToPath(import.meta.url));
  } catch {
    return cwd();
  }
}
function detectContainerSession(projectPath) {
  let currentDir = cwd(), sessionId = env.AGENT_CONTAINER_SESSION_ID, ownDir = scriptDir(), config = readSessionFromFile(ownDir, sessionId);
  if (config)
    return config;
  if (sessionId) {
    let workspacePath = env.AGENT_CONTAINER_WORKSPACE_PATH || "/workspace";
    return { sessionId, workspacePath };
  }
  return null;
}
function readSessionFromFile(dir, targetSessionId) {
  let filePath = path3.join(dir, CONTAINER_SESSION_FILE);
  try {
    let lines = readFileSync(filePath, "utf-8").split(`
`).filter((l) => l.trim()), lastConfig = null;
    for (let line of lines)
      try {
        let data = JSON.parse(line);
        if (typeof data.sessionId !== "string" || !data.sessionId)
          continue;
        let config = {
          sessionId: data.sessionId,
          workspacePath: typeof data.workspacePath === "string" ? data.workspacePath : "/workspace"
        };
        if (typeof data.mcpStreamUrl === "string")
          config.mcpStreamUrl = data.mcpStreamUrl;
        if (typeof data.projectPath === "string")
          config.projectPath = data.projectPath.replace(/\\/g, "/");
        if (typeof data.buildCommand === "string")
          config.buildCommand = data.buildCommand;
        if (targetSessionId && data.sessionId === targetSessionId)
          return config;
        lastConfig = config;
      } catch {}
    if (!targetSessionId && lastConfig)
      return lastConfig;
  } catch {}
  return null;
}
function toContainerPath(workspacePath, relativePath) {
  if (relativePath.startsWith("/"))
    return relativePath;
  return `${workspacePath}/${relativePath}`;
}

// proxy-tools/container-handlers.ts
function toPosix(p) {
  return p.replace(/\\/g, "/");
}
function resolveContainerFilePath(filePath, session, projectPath) {
  let posixFilePath = toPosix(filePath), posixProjectPath = toPosix(projectPath);
  if (posixFilePath.startsWith(session.workspacePath))
    return posixFilePath;
  if (posixFilePath.startsWith(posixProjectPath + "/"))
    return session.workspacePath + "/" + posixFilePath.substring(posixProjectPath.length + 1);
  if (posixFilePath === posixProjectPath)
    return session.workspacePath;
  if (!path4.isAbsolute(filePath))
    return toContainerPath(session.workspacePath, posixFilePath);
  throw Error(`Refusing to resolve absolute path '${filePath}' \u2014 not under session workspace '${session.workspacePath}' or project path '${projectPath}'. In container mode paths must remain inside the workspace mount.`);
}
function tagContainer(session, text) {
  return `[container:${session.sessionId}] ${text}`;
}
function extractText(result) {
  if (typeof result === "string")
    return result;
  if (result && typeof result === "object") {
    let r = result;
    if (typeof r.text === "string")
      return r.text;
    if (Array.isArray(r.content)) {
      for (let item of r.content)
        if (item && typeof item.text === "string")
          return item.text;
    }
  }
  return "";
}
function resolveSearchPath(args, session, projectPath) {
  let rawPath = typeof args.searchPath === "string" ? args.searchPath : typeof args.path === "string" ? args.path : void 0;
  if (!rawPath)
    return session.workspacePath;
  return resolveContainerFilePath(rawPath, session, projectPath);
}
async function handleContainerSearchText(args, projectPath, callUpstreamTool, session) {
  let query = requireString(args.q ?? args.query, "q"), limit = typeof args.limit === "number" ? args.limit : 50, searchPath = resolveSearchPath(args, session, projectPath);
  return tagContainer(session, extractText(await callUpstreamTool("container_search_text", {
    sessionId: session.sessionId,
    q: query,
    searchPath,
    limit
  })));
}
async function handleContainerSearchRegex(args, projectPath, callUpstreamTool, session) {
  let pattern = requireString(args.pattern ?? args.q, "pattern"), limit = typeof args.limit === "number" ? args.limit : 50, searchPath = resolveSearchPath(args, session, projectPath);
  return tagContainer(session, extractText(await callUpstreamTool("container_search_regex", {
    sessionId: session.sessionId,
    pattern,
    searchPath,
    limit
  })));
}
async function handleContainerSearchFile(args, projectPath, callUpstreamTool, session) {
  let pattern = requireString(args.pattern ?? args.glob, "pattern"), limit = typeof args.limit === "number" ? args.limit : 100, searchPath = resolveSearchPath(args, session, projectPath);
  return tagContainer(session, extractText(await callUpstreamTool("container_search_file", {
    sessionId: session.sessionId,
    pattern,
    searchPath,
    limit
  })));
}
async function handleContainerBash(args, projectPath, callUpstreamTool, session) {
  let command = requireString(args.command, "command");
  if (projectPath) {
    command = command.replaceAll(projectPath, session.workspacePath);
    let posixProjectPath = toPosix(projectPath);
    if (posixProjectPath !== projectPath)
      command = command.replaceAll(posixProjectPath, session.workspacePath);
  }
  let timeoutMs = typeof args.timeout === "number" ? args.timeout : 900000, result = extractText(await callUpstreamTool("container_exec", {
    sessionId: session.sessionId,
    command: ["bash", "-c", `cd '${session.workspacePath}' && ${command}`],
    timeoutMs
  }));
  return tagContainer(session, result);
}

// proxy-tools/schemas.ts
function objectSchema(properties, required) {
  return {
    type: "object",
    properties,
    required: required && required.length > 0 ? required : void 0,
    additionalProperties: !1
  };
}
function createSearchSchema(qDescription) {
  return objectSchema({
    q: {
      type: "string",
      description: qDescription
    },
    paths: {
      type: "array",
      description: "Optional list of project-relative glob patterns (supports ! excludes).",
      items: {
        type: "string"
      }
    },
    limit: {
      type: "number",
      description: "Maximum number of results to return."
    }
  }, ["q"]);
}
function createSearchTextSchema() {
  return createSearchSchema("Text substring to search for.");
}
function createSearchRegexSchema() {
  return createSearchSchema("Regular expression pattern to search for.");
}
function createSearchFileSchema() {
  let base = createSearchSchema("Glob pattern to match file paths.");
  return objectSchema({
    ...base.properties,
    includeExcluded: {
      type: "boolean",
      description: "Whether to include excluded/ignored files in results."
    }
  }, base.required);
}
function createRenameSchema() {
  return objectSchema({
    pathInProject: {
      type: "string",
      description: "Absolute or project-relative path to the file containing the symbol (for example, src/app.ts)."
    },
    symbolName: {
      type: "string",
      description: "Exact, case-sensitive name of the symbol to rename."
    },
    newName: {
      type: "string",
      description: "New, case-sensitive name for the symbol."
    }
  }, ["pathInProject", "symbolName", "newName"]);
}

// proxy-tools/registry.ts
var BLOCKED_TOOL_NAMES = /* @__PURE__ */ new Set([
  "read_file",
  "apply_patch",
  "create_new_file",
  "list_dir",
  "list_directory_tree",
  "container_read_file",
  "container_write_file",
  "container_list_dir",
  "execute_terminal_command",
  "execute_tool",
  "skill_search",
  "build_project"
]), EXTRA_REPLACED_TOOL_NAMES = [
  "get_file_problems"
], RENAME_TOOL_DESCRIPTION = "Rename a symbol (class/function/variable/etc.) using IDE refactoring. Updates all references across the project; do not use text replacement for renames.", READ_ONLY_TOOL_ANNOTATIONS = { readOnlyHint: !0, openWorldHint: !1 };
function resolveToolDescription(description, context) {
  return typeof description === "function" ? description(context) : description;
}
function resolveToolExpose(expose, context) {
  if (expose === void 0)
    return !0;
  if (typeof expose === "function")
    return expose(context);
  return expose !== !1;
}
function buildToolSpec(name, description, inputSchema, annotations, context) {
  return {
    name,
    description: resolveToolDescription(description, context),
    inputSchema: withTimeoutDeclared(inputSchema),
    ...annotations ? { annotations } : {}
  };
}
var TIMEOUT_INPUT_SCHEMA_PROPERTY = {
  type: "number",
  description: "Optional. Per-call timeout in milliseconds. Used as the ij-proxy MCP RPC deadline and forwarded to upstream tools that accept it. 0 disables. Defaults to the proxy's configured per-tool timeout (~60 s for most tools, ~1200 s for build/lint/container)."
};
function withTimeoutDeclared(inputSchema) {
  if (Object.prototype.hasOwnProperty.call(inputSchema.properties, "timeout"))
    return inputSchema;
  return {
    ...inputSchema,
    properties: { ...inputSchema.properties, timeout: TIMEOUT_INPUT_SCHEMA_PROPERTY }
  };
}
var TOOL_VARIANTS = [
  {
    name: "search_text",
    description: "Search for a text substring in project files.",
    schemaFactory: () => createSearchTextSchema(),
    handlerFactory: ({ projectPath, callUpstreamToolRaw, containerSession }) => {
      if (!containerSession)
        throw Error("search_text is proxied only in container mode");
      return (args) => handleContainerSearchText(args, projectPath, callUpstreamToolRaw, containerSession);
    },
    annotations: READ_ONLY_TOOL_ANNOTATIONS,
    expose: ({ containerSession }) => containerSession != null
  },
  {
    name: "search_regex",
    description: "Search for a regular expression in project files.",
    schemaFactory: () => createSearchRegexSchema(),
    handlerFactory: ({ projectPath, callUpstreamToolRaw, containerSession }) => {
      if (!containerSession)
        throw Error("search_regex is proxied only in container mode");
      return (args) => handleContainerSearchRegex(args, projectPath, callUpstreamToolRaw, containerSession);
    },
    annotations: READ_ONLY_TOOL_ANNOTATIONS,
    expose: ({ containerSession }) => containerSession != null
  },
  {
    name: "search_file",
    description: "Search for files using a glob pattern.",
    schemaFactory: () => createSearchFileSchema(),
    handlerFactory: ({ projectPath, callUpstreamToolRaw, containerSession }) => {
      if (!containerSession)
        throw Error("search_file is proxied only in container mode");
      return (args) => handleContainerSearchFile(args, projectPath, callUpstreamToolRaw, containerSession);
    },
    annotations: READ_ONLY_TOOL_ANNOTATIONS,
    expose: ({ containerSession }) => containerSession != null
  },
  {
    name: "rename",
    description: RENAME_TOOL_DESCRIPTION,
    schemaFactory: () => createRenameSchema(),
    handlerFactory: ({ projectPath, callUpstreamTool }) => (args) => handleRenameTool(args, projectPath, callUpstreamTool),
    upstreamNames: ["rename_refactoring"]
  },
  {
    name: "bash",
    description: "Execute a bash command in the project workspace (runs inside Docker container when container session is active).",
    schemaFactory: () => ({
      type: "object",
      properties: {
        command: { type: "string", description: "The bash command to execute" },
        timeout: { type: "number", description: "Per-call timeout in milliseconds. Used as the ij-proxy MCP RPC deadline and as the inner container_exec command deadline. 0 disables. Default: 900000 (15 min); use 1200000+ for build commands." }
      },
      required: ["command"]
    }),
    handlerFactory: ({ projectPath, callUpstreamToolRaw, containerSession }) => {
      if (!containerSession)
        throw Error("bash tool is only available in container mode");
      return (args) => handleContainerBash(args, projectPath, callUpstreamToolRaw, containerSession);
    },
    expose: ({ containerSession }) => containerSession != null
  }
];
function isExposedVariant(tool, context) {
  return resolveToolExpose(tool.expose, context);
}
function buildProxyToolingData(context) {
  let variants = TOOL_VARIANTS.filter((tool) => isExposedVariant(tool, context)), handlers = /* @__PURE__ */ new Map;
  for (let tool of variants)
    handlers.set(tool.name, tool.handlerFactory(context));
  return {
    proxyToolSpecs: variants.map((tool) => buildToolSpec(tool.name, tool.description, tool.schemaFactory(context), tool.annotations, context)),
    proxyToolNames: new Set(variants.map((tool) => tool.name)),
    handlers
  };
}
function getReplacedToolNames() {
  let replaced = new Set(EXTRA_REPLACED_TOOL_NAMES);
  for (let tool of TOOL_VARIANTS) {
    if (!tool.upstreamNames)
      continue;
    for (let name of tool.upstreamNames) {
      if (name === tool.name)
        continue;
      replaced.add(name);
    }
  }
  return replaced;
}

// proxy-tools/tooling.ts
function resolveUpstreamToolSupport(upstreamTools) {
  let hasLintFiles = !1, hasReformatFile = !1;
  for (let tool of upstreamTools ?? [])
    if (tool?.name === "lint_files")
      hasLintFiles = !0;
    else if (tool?.name === "reformat_file")
      hasReformatFile = !0;
  return { hasLintFiles, hasReformatFile };
}
function createProxyTooling({
  projectPath,
  callUpstreamTool,
  callUpstreamToolRaw,
  containerSession
}) {
  let { proxyToolSpecs, proxyToolNames, handlers } = buildProxyToolingData({
    projectPath,
    callUpstreamTool,
    callUpstreamToolRaw: callUpstreamToolRaw ?? callUpstreamTool,
    containerSession: containerSession ?? null
  });
  async function runProxyToolCall(toolName, args) {
    let handler = handlers.get(toolName);
    if (!handler)
      throw Error(`Unknown tool: ${toolName}`);
    return await handler(args);
  }
  return { proxyToolSpecs, proxyToolNames, runProxyToolCall };
}

// upstream.ts
var requestContext = new AsyncLocalStorage, RECOVERABLE_UPSTREAM_ERROR_RE = /\b(not connected|connection closed|session not found|server not initialized|mcp-session-id header is required)\b/i;
function getErrorMessage(error) {
  return error instanceof Error ? error.message : String(error);
}
function isRecoverableUpstreamError(error) {
  return RECOVERABLE_UPSTREAM_ERROR_RE.test(getErrorMessage(error));
}
function normalizeToolResult(result) {
  if (result && typeof result === "object" && "toolResult" in result)
    return result.toolResult;
  return result;
}

class UpstreamConnection {
  client;
  _transport;
  _projectPathManager;
  _defaultProjectPathKey;
  _forceInjectProjectPath;
  _connectTimeoutMs;
  _toolCallTimeoutMs;
  _buildTimeoutMs;
  _warn;
  _connectedPromise = null;
  _tools = null;
  toolSupport = resolveUpstreamToolSupport([]);
  ideVersion = null;
  onStateChange;
  constructor(options) {
    this._transport = options.transport, this._connectTimeoutMs = options.connectTimeoutMs, this._toolCallTimeoutMs = options.toolCallTimeoutMs, this._buildTimeoutMs = options.buildTimeoutMs, this._warn = options.warn, this._defaultProjectPathKey = options.defaultProjectPathKey, this._forceInjectProjectPath = options.forceInjectProjectPath ?? !1, this._projectPathManager = createProjectPathManager({
      projectPath: options.projectPath,
      defaultProjectPathKey: options.defaultProjectPathKey,
      forceInject: this._forceInjectProjectPath
    }), this.client = new Client({ name: "ij-mcp-proxy", version: "1.0.0" }), this.client.onerror = (error) => {
      this._warn(`Upstream client error: ${error.message}`);
    }, this.client.onclose = () => {
      this.reset(), this._warn("Upstream client connection closed; will reconnect on next request");
    };
  }
  updateProjectPath(newProjectPath) {
    this._projectPathManager = createProjectPathManager({
      projectPath: newProjectPath,
      defaultProjectPathKey: this._defaultProjectPathKey,
      forceInject: this._forceInjectProjectPath
    }), this._reapplyToolScan();
  }
  _reapplyToolScan() {
    if (this._tools)
      this._projectPathManager.updateProjectPathKeys(this._tools);
  }
  async connect() {
    if (!this.client.transport)
      this._connectedPromise = null, this._tools = null;
    if (this._connectedPromise)
      return this._connectedPromise;
    let options = this._connectTimeoutMs > 0 ? { timeout: this._connectTimeoutMs } : void 0;
    return this._connectedPromise = this.client.connect(this._transport, options).catch((error) => {
      throw this._connectedPromise = null, error;
    }), this._connectedPromise = this._connectedPromise.then(() => {
      this._updateIdeVersion();
    }), this._connectedPromise;
  }
  reset() {
    this._connectedPromise = null, this._tools = null, this.toolSupport = resolveUpstreamToolSupport([]), this.ideVersion = null, this.onStateChange?.();
  }
  async withReconnect(label, fn) {
    try {
      return await fn();
    } catch (error) {
      if (!isRecoverableUpstreamError(error))
        throw error;
      this._warn(`Upstream ${label} failed (${getErrorMessage(error)}); reconnecting and retrying once`), this.reset();
      try {
        await this._transport.resetTransport(error);
      } catch (resetError) {
        this._warn(`Failed to reset MCP stream transport: ${getErrorMessage(resetError)}`);
      }
      return await this.connect(), fn();
    }
  }
  async refreshTools() {
    return await this.withReconnect("tools/list", async () => {
      await this.connect();
      let response = await this.client.listTools(), tools = Array.isArray(response?.tools) ? response.tools : [];
      return this._projectPathManager.updateProjectPathKeys(tools), this._projectPathManager.stripProjectPathFromTools(tools), this._tools = tools, this.toolSupport = resolveUpstreamToolSupport(tools), this.onStateChange?.(), tools;
    });
  }
  async getTools() {
    if (!this._tools)
      await this.refreshTools();
    return this._tools ?? [];
  }
  async callToolRaw(toolName, args) {
    return await this.withReconnect(`tools/call ${toolName}`, async () => {
      await this.connect(), await this.getTools();
      let callArgs = this._forceInjectProjectPath ? { ...args } : args;
      if (this._forceInjectProjectPath)
        this._projectPathManager.injectProjectPathArgs(toolName, callArgs);
      let timeoutMs = this._resolveTimeoutMs(toolName), options = timeoutMs > 0 ? { timeout: timeoutMs } : void 0, result = normalizeToolResult(await this.client.callTool({ name: toolName, arguments: callArgs }, void 0, options));
      if (result?.isError)
        throw Error(extractTextFromResult(result) || "Upstream tool error");
      return result;
    });
  }
  async callTool(toolName, args) {
    return await this.withReconnect(`tools/call ${toolName}`, async () => {
      await this.connect(), await this.getTools();
      let callArgs = { ...args };
      this._projectPathManager.injectProjectPathArgs(toolName, callArgs);
      let timeoutMs = this._resolveTimeoutMs(toolName), options = timeoutMs > 0 ? { timeout: timeoutMs } : void 0, startTime = Date.now(), result;
      try {
        result = normalizeToolResult(await this.client.callTool({ name: toolName, arguments: callArgs }, void 0, options));
      } catch (error) {
        let elapsed = Date.now() - startTime;
        throw this._warn(`Upstream ${toolName} failed after ${elapsed}ms (timeout: ${timeoutMs}ms): ${getErrorMessage(error)}`), error;
      }
      if (result?.isError)
        throw Error(extractTextFromResult(result) || "Upstream tool error");
      return result;
    });
  }
  async callToolForClient(toolName, args) {
    return await this.withReconnect(`tools/call ${toolName}`, async () => {
      await this.connect(), await this.getTools(), this._projectPathManager.injectProjectPathArgs(toolName, args);
      let timeoutMs = this._resolveTimeoutMs(toolName), options = timeoutMs > 0 ? { timeout: timeoutMs } : void 0, startTime = Date.now();
      try {
        let result = await this.client.callTool({ name: toolName, arguments: args }, void 0, options);
        return normalizeToolResult(result);
      } catch (error) {
        let elapsed = Date.now() - startTime;
        throw this._warn(`Upstream ${toolName} failed after ${elapsed}ms (timeout: ${timeoutMs}ms): ${getErrorMessage(error)}`), error;
      }
    });
  }
  static _LONG_TIMEOUT_TOOLS = /* @__PURE__ */ new Set(["lint_files", "reformat_file", "open_file_in_editor", "container_exec"]);
  _resolveTimeoutMs(toolName) {
    let ctx = requestContext.getStore();
    if (ctx?.clientTimeoutMs !== void 0)
      return ctx.clientTimeoutMs;
    return UpstreamConnection._LONG_TIMEOUT_TOOLS.has(toolName) ? this._buildTimeoutMs : this._toolCallTimeoutMs;
  }
  async forwardRequest(method, params) {
    return await this.withReconnect(method, async () => (await this.connect(), await this.client.request({ method, params }, ResultSchema)));
  }
  async forwardNotification(notification) {
    await this.withReconnect(notification.method, async () => {
      await this.connect(), await this.client.notification(notification);
    });
  }
  _updateIdeVersion() {
    let serverInfo = this.client.getServerVersion();
    this.ideVersion = typeof serverInfo?.version === "string" ? serverInfo.version : null;
  }
}

// node_modules/is-port-reachable/index.js
import net from "net";
async function isPortReachable(port, { host, timeout = 1000 } = {}) {
  if (typeof host !== "string")
    throw TypeError("Specify a `host`");
  let promise = new Promise((resolve, reject) => {
    let socket = new net.Socket, onError = () => {
      socket.destroy(), reject();
    };
    socket.setTimeout(timeout), socket.once("error", onError), socket.once("timeout", onError), socket.connect(port, host, () => {
      socket.end(), resolve();
    });
  });
  try {
    return await promise, !0;
  } catch {
    return !1;
  }
}

// discovery.ts
function buildCandidateList(preferredPorts, portScanStart, portScanLimit) {
  let seen = /* @__PURE__ */ new Set, candidates = [];
  for (let port of preferredPorts) {
    if (!Number.isFinite(port) || port <= 0 || seen.has(port))
      continue;
    seen.add(port), candidates.push(port);
  }
  let limit = Number.isFinite(portScanLimit) && portScanLimit > 0 ? portScanLimit : 0, start = Number.isFinite(portScanStart) && portScanStart > 0 ? portScanStart : 0;
  for (let i = 0;i < limit; i++) {
    let port = start + i;
    if (port <= 0 || seen.has(port))
      continue;
    seen.add(port), candidates.push(port);
  }
  return candidates;
}
async function findReachablePorts(options) {
  let { preferredPorts, portScanStart, portScanLimit, scanTimeoutMs, buildUrl, probeHost = "127.0.0.1", warn } = options, candidates = buildCandidateList(preferredPorts, portScanStart, portScanLimit);
  if (candidates.length === 0)
    return [];
  let probeResults = await Promise.allSettled(candidates.map(async (port) => {
    let reachable = await isPortReachable(port, {
      host: probeHost,
      timeout: scanTimeoutMs > 0 ? scanTimeoutMs : void 0
    });
    return { port, reachable };
  })), result = [];
  for (let probeResult of probeResults)
    if (probeResult.status === "fulfilled" && probeResult.value.reachable) {
      let port = probeResult.value.port;
      result.push({ port, url: buildUrl(port) });
    }
  if (result.length === 0 && warn)
    warn(`No reachable MCP stream ports found. Probed: ${candidates.join(", ")}`);
  return result;
}

// routing.ts
import path5 from "path";
var RIDER_PROJECT_SUBPATH = "dotnet", MERGE_TOOL_NAMES = /* @__PURE__ */ new Set([
  "search_text",
  "search_regex",
  "search_file",
  "search_symbol"
]), SPLIT_MERGE_TOOL_NAMES = /* @__PURE__ */ new Set([
  "lint_files",
  "reformat_file"
]);
function resolveRoute(toolName, args, projectRoot) {
  if (MERGE_TOOL_NAMES.has(toolName))
    return "merge";
  if (SPLIT_MERGE_TOOL_NAMES.has(toolName))
    return "split-merge";
  return resolveIdeForPath(args, projectRoot) === "rider" ? "target-rider" : "primary";
}
function rewriteArgsForTarget(route, args) {
  if (route !== "target-rider")
    return { ...args };
  let rewritten = { ...args };
  for (let key of PATH_ARG_KEYS) {
    let value = rewritten[key];
    if (typeof value === "string" && value.length > 0)
      rewritten[key] = stripRiderPrefix(value);
  }
  return rewritten;
}
function stripRiderPrefix(filePath) {
  if (filePath.startsWith(RIDER_PROJECT_SUBPATH + "/"))
    return filePath.slice(RIDER_PROJECT_SUBPATH.length + 1);
  if (filePath.startsWith(RIDER_PROJECT_SUBPATH + "\\"))
    return filePath.slice(RIDER_PROJECT_SUBPATH.length + 1);
  if (filePath === RIDER_PROJECT_SUBPATH)
    return "";
  return filePath;
}
function isMergeTool(toolName) {
  return MERGE_TOOL_NAMES.has(toolName);
}
function createPathPrefixTransformer(prefix) {
  return (items) => items.map((item) => ({
    ...item,
    filePath: prefix + "/" + item.filePath
  }));
}
var riderItemTransformer = createPathPrefixTransformer(RIDER_PROJECT_SUBPATH);
function resolveIdeForPath(args, projectRoot) {
  let filePath = extractPathArg(args);
  return filePath != null && isRiderPath(filePath, projectRoot) ? "rider" : "idea";
}
function isRiderPath(filePath, projectRoot) {
  if (!filePath)
    return !1;
  let absolute = path5.isAbsolute(filePath) ? path5.normalize(filePath) : path5.resolve(projectRoot, filePath), relative = path5.relative(projectRoot, absolute);
  if (relative.startsWith("..") || path5.isAbsolute(relative))
    return !1;
  return relative === RIDER_PROJECT_SUBPATH || relative.startsWith(RIDER_PROJECT_SUBPATH + path5.sep);
}
function splitPathListArgsByIde(args, projectRoot, argName = "files") {
  let rawPaths = args[argName];
  if (!Array.isArray(rawPaths))
    throw Error(`${argName} must be an array of strings`);
  let normalizedPaths = rawPaths.map((rawPath) => {
    if (typeof rawPath !== "string" || rawPath.trim().length === 0)
      throw Error(`${argName} must contain non-empty strings`);
    return rawPath.trim();
  });
  if (normalizedPaths.length === 0)
    throw Error(`${argName} must contain at least one path`);
  let ideaPaths = [], riderPaths = [];
  for (let filePath of normalizedPaths)
    if (isRiderPath(filePath, projectRoot))
      riderPaths.push(stripRiderPrefix(filePath));
    else
      ideaPaths.push(filePath);
  return {
    ideaArgs: ideaPaths.length > 0 ? { ...args, [argName]: ideaPaths } : void 0,
    riderArgs: riderPaths.length > 0 ? { ...args, [argName]: riderPaths } : void 0
  };
}
var PATH_ARG_KEYS = ["pathInProject", "directoryPath", "filePath"];
function extractPathArg(args) {
  for (let key of PATH_ARG_KEYS) {
    let value = args[key];
    if (typeof value === "string" && value.length > 0)
      return value;
  }
  return;
}

// proxy-tools/handlers/reformat-file.ts
function normalizeReformatFileArgs(args) {
  return {
    ...args,
    files: normalizeReformatFileFiles(args)
  };
}
function normalizeReformatFileFiles(args) {
  if (Object.prototype.hasOwnProperty.call(args, "path"))
    throw Error("path is no longer supported; use files");
  if (Object.prototype.hasOwnProperty.call(args, "paths"))
    throw Error("paths is no longer supported; use files");
  let rawFiles = args.files;
  if (!Array.isArray(rawFiles))
    throw Error("files must be an array of non-empty strings");
  let result = [], seen = /* @__PURE__ */ new Set;
  for (let rawFile of rawFiles)
    addFile(rawFile, result, seen);
  if (result.length === 0)
    throw Error("files must contain at least one path");
  return result;
}
function addFile(value, result, seen) {
  let path = requireString(value, "files").trim();
  if (path.length === 0)
    throw Error("files must contain non-empty strings");
  if (seen.has(path))
    return;
  seen.add(path), result.push(path);
}

// ij-mcp-proxy.ts
var explicitMcpUrl = env2.JETBRAINS_MCP_STREAM_URL || env2.MCP_STREAM_URL || env2.JETBRAINS_MCP_URL || env2.MCP_URL, defaultHost = "127.0.0.1", defaultPort = 64342, defaultPath = "/stream", defaultScanLimit = 10, portScanStartEnv = env2.JETBRAINS_MCP_PORT_START, portScanStart = parseEnvInt("JETBRAINS_MCP_PORT_START", defaultPort), portScanLimit = parseEnvInt("JETBRAINS_MCP_PORT_SCAN_LIMIT", defaultScanLimit), preferredPorts = portScanStartEnv ? [portScanStart] : [defaultPort, 64344], connectTimeoutMs = parseEnvSeconds("JETBRAINS_MCP_CONNECT_TIMEOUT_S", 10), scanTimeoutMs = parseEnvSeconds("JETBRAINS_MCP_SCAN_TIMEOUT_S", 1), queueLimit = parseEnvNonNegativeInt("JETBRAINS_MCP_QUEUE_LIMIT", 100), toolCallTimeoutMs = parseEnvSeconds("JETBRAINS_MCP_TOOL_CALL_TIMEOUT_S", 60), buildTimeoutMs = parseEnvSeconds("JETBRAINS_MCP_BUILD_TIMEOUT_S", 1200), queueWaitTimeoutMs = parseEnvSeconds("JETBRAINS_MCP_QUEUE_WAIT_TIMEOUT_S", toolCallTimeoutMs > 0 ? Math.round(toolCallTimeoutMs / 1000) : 0), STREAM_RETRY_ATTEMPTS = 3, STREAM_RETRY_BASE_DELAY_MS = 200, IJ_MCP_CLIENT_TAGS = "IJ_MCP_CLIENT_TAGS", AIR_CONTAINER_CLIENT_TAG_PREFIX = "air-container:", PROJECT_MATCH_PROBE_TOOLS = [
  { toolName: "get_all_open_file_paths", args: {} },
  { toolName: "get_project_dependencies", args: {} },
  { toolName: "get_project_modules", args: {} }
], PROJECT_MISMATCH_RE = /\bdoesn['\u2019]t correspond to any open project\b|\bNo exact project is specified while multiple projects are opened\b|\bCurrently open projects:\b/i;
function parseEnvInt(name, fallback) {
  let raw = env2[name];
  if (!raw)
    return fallback;
  let parsed = Number.parseInt(raw, 10);
  if (!Number.isFinite(parsed) || parsed <= 0)
    return fallback;
  return parsed;
}
function parseEnvNonNegativeInt(name, fallback) {
  let raw = env2[name];
  if (raw === void 0 || raw === null || raw === "")
    return fallback;
  let parsed = Number.parseInt(raw, 10);
  if (!Number.isFinite(parsed) || parsed < 0)
    return fallback;
  return parsed;
}
function parseEnvSeconds(name, fallbackSeconds) {
  return parseEnvNonNegativeInt(name, fallbackSeconds) * 1000;
}
function buildStreamUrl(port) {
  return `http://${defaultHost}:${port}${defaultPath}`;
}
function resolveProjectPath(rawValue) {
  if (!rawValue)
    return { projectPath: path6.resolve(cwd2()) };
  if (rawValue.startsWith("file://"))
    try {
      return { projectPath: path6.resolve(fileURLToPath2(new URL(rawValue))) };
    } catch (error) {
      let message = error instanceof Error ? error.message : String(error);
      return {
        projectPath: path6.resolve(rawValue),
        warning: `Failed to parse JETBRAINS_MCP_PROJECT_PATH as a file URI (${message}); falling back to path resolution.`
      };
    }
  return { projectPath: path6.resolve(rawValue) };
}
var explicitProjectPath = env2.JETBRAINS_MCP_PROJECT_PATH, projectPathResolution = resolveProjectPath(explicitProjectPath), projectPath = projectPathResolution.projectPath, defaultProjectPathKey = "projectPath", containerSession = detectContainerSession(projectPath), explicitMcpUrlOverride;
if (containerSession?.mcpStreamUrl)
  explicitMcpUrlOverride = containerSession.mcpStreamUrl;
if (containerSession?.projectPath)
  projectPath = containerSession.projectPath;
var REPLACED_TOOL_NAMES = getReplacedToolNames(), BASE_BLOCKED_TOOL_NAMES = /* @__PURE__ */ new Set([...BLOCKED_TOOL_NAMES, ...REPLACED_TOOL_NAMES]), ideaUpstream = null, riderUpstream = null, discoveryPromise = null, proxyToolSpecs = [], proxyToolNames = /* @__PURE__ */ new Set, ideaProxyToolCall = null, riderProxyToolCall = null;
function primaryUpstream() {
  let upstream = ideaUpstream ?? riderUpstream;
  if (!upstream)
    throw Error("No upstream connection available");
  return upstream;
}
function updateProxyTooling() {
  let ideaSpecs = [], ideaNames = /* @__PURE__ */ new Set;
  if (ideaUpstream) {
    let tooling = createProxyTooling({
      projectPath,
      callUpstreamTool: (name, args) => ideaUpstream.callTool(name, args),
      callUpstreamToolRaw: (name, args) => ideaUpstream.callToolRaw(name, args),
      containerSession
    });
    ideaSpecs = tooling.proxyToolSpecs, ideaNames = tooling.proxyToolNames, ideaProxyToolCall = tooling.runProxyToolCall;
  } else
    ideaProxyToolCall = null;
  let riderSpecs = [], riderNames = /* @__PURE__ */ new Set;
  if (riderUpstream) {
    let riderProjectPath = path6.join(projectPath, RIDER_PROJECT_SUBPATH), tooling = createProxyTooling({
      projectPath: riderProjectPath,
      callUpstreamTool: (name, args) => riderUpstream.callTool(name, args),
      callUpstreamToolRaw: (name, args) => riderUpstream.callToolRaw(name, args),
      containerSession
    });
    riderSpecs = tooling.proxyToolSpecs, riderNames = tooling.proxyToolNames, riderProxyToolCall = tooling.runProxyToolCall;
  } else
    riderProxyToolCall = null;
  proxyToolSpecs = mergeToolLists(ideaSpecs, riderSpecs, /* @__PURE__ */ new Set), proxyToolNames = /* @__PURE__ */ new Set([...ideaNames, ...riderNames]);
}
async function activateDetectedContainerSession() {
  if (containerSession)
    return !1;
  let detected = detectContainerSession(projectPath);
  if (!detected)
    return !1;
  if (containerSession = detected, note(`Container session detected (lazy): id=${detected.sessionId}, workspace=${detected.workspacePath}`), detected.projectPath)
    projectPath = detected.projectPath;
  if (detected.mcpStreamUrl)
    explicitMcpUrlOverride = detected.mcpStreamUrl;
  let staleUpstreams = [ideaUpstream, riderUpstream].filter((upstream) => upstream != null);
  return ideaUpstream = null, riderUpstream = null, discoveryPromise = null, await Promise.allSettled(staleUpstreams.map(async (upstream) => upstream.client.close())), updateProxyTooling(), !0;
}
function note(message) {
  logToFile(message), logProgress(message);
}
var warn = note;
function buildInstructions() {
  let ides = [];
  if (ideaUpstream) {
    let name = ideaUpstream.client.getServerVersion()?.name ?? "IntelliJ IDEA", version = ideaUpstream.ideVersion;
    ides.push(version ? `${name} ${version}` : name);
  }
  if (riderUpstream) {
    let name = riderUpstream.client.getServerVersion()?.name ?? "JetBrains Rider", version = riderUpstream.ideVersion;
    ides.push(version ? `${name} ${version}` : name);
  }
  if (ides.length === 0 && !containerSession)
    return;
  let parts = [];
  if (ides.length > 0)
    parts.push(`Connected IDEs: ${ides.join(", ")}.`);
  if (containerSession)
    parts.push(`CONTAINER MODE ACTIVE: This session operates on a Docker container (session ${containerSession.sessionId}).`, "Search operations (search_text, search_regex, search_file) are routed to the container. Use the agent's native file tools for reads, writes, patches, and directory listing.", "Semantic tools (search_symbol, lint_files, rename) use the host IDE index.", 'Use the "bash" tool for ALL shell commands \u2014 it executes inside the container. Do NOT use your built-in Bash tool or execute_terminal_command, as they run on the host, not in the container.', "The container has: git, curl, ripgrep (rg), patch, java (JBR 21), bazel (via Bazelisk). All tools are in PATH.", `IMPORTANT: Before completing your task, verify your changes compile by running the build command inside the container${containerSession.buildCommand ? `: \`${containerSession.buildCommand}\`` : ""}. Fix any compilation errors before finishing.`);
  return parts.join(`
`);
}
clearLogFile();
if (projectPathResolution.warning)
  warn(projectPathResolution.warning);
if (containerSession)
  note(`Container session detected: id=${containerSession.sessionId}, workspace=${containerSession.workspacePath}`);
function createUpstreamForUrl(url) {
  let transport = createStreamTransport({
    url,
    requestHeaders: containerSession ? { [IJ_MCP_CLIENT_TAGS]: `${AIR_CONTAINER_CLIENT_TAG_PREFIX}${containerSession.sessionId}` } : void 0,
    queueLimit,
    queueWaitTimeoutMs,
    retryAttempts: STREAM_RETRY_ATTEMPTS,
    retryBaseDelayMs: STREAM_RETRY_BASE_DELAY_MS,
    note,
    warn
  }), conn = new UpstreamConnection({
    transport,
    projectPath,
    defaultProjectPathKey,
    connectTimeoutMs,
    forceInjectProjectPath: containerSession != null,
    toolCallTimeoutMs,
    buildTimeoutMs,
    warn
  });
  return conn.onStateChange = () => updateProxyTooling(), conn;
}
function setupUpstreamClientHandlers(conn) {
  conn.client.setNotificationHandler(ToolListChangedNotificationSchema, async () => {
    try {
      await conn.refreshTools(), await proxyServer.sendToolListChanged();
    } catch (error) {
      let message = error instanceof Error ? error.message : String(error);
      warn(`Failed to refresh tool list after upstream change: ${message}`);
    }
  }), conn.client.fallbackRequestHandler = async (request) => await proxyServer.request({ method: request.method, params: request.params }, ResultSchema), conn.client.fallbackNotificationHandler = async (notification) => {
    try {
      await proxyServer.notification(notification);
    } catch (error) {
      let message = error instanceof Error ? error.message : String(error);
      warn(`Failed to forward upstream notification: ${message}`);
    }
  };
}
function isRiderServerName(name) {
  return /rider/i.test(name);
}
function formatUpstream(candidate) {
  return `${candidate.url} (${candidate.name})`;
}
function isProjectMismatchError(error) {
  let message = error instanceof Error ? error.message : String(error);
  return PROJECT_MISMATCH_RE.test(message);
}
async function probeProjectMatch(candidate) {
  let tools = await candidate.conn.getTools(), availableToolNames = new Set(tools.map((tool) => tool.name));
  for (let probe of PROJECT_MATCH_PROBE_TOOLS) {
    if (!availableToolNames.has(probe.toolName))
      continue;
    try {
      return await candidate.conn.callTool(probe.toolName, { ...probe.args }), "match";
    } catch (error) {
      if (isProjectMismatchError(error))
        return "mismatch";
      let message = error instanceof Error ? error.message : String(error);
      warn(`Failed to verify injected project path for ${formatUpstream(candidate)} via ${probe.toolName}: ${message}`);
    }
  }
  return "unknown";
}
async function chooseUpstreamForProject(candidates, ideLabel, targetProjectPath) {
  if (candidates.length === 0)
    return null;
  if (candidates.length === 1)
    return candidates[0];
  let unknownCandidates = [];
  for (let candidate of candidates) {
    let matchStatus = await probeProjectMatch(candidate);
    if (matchStatus === "match")
      return candidate;
    if (matchStatus === "unknown") {
      unknownCandidates.push(candidate);
      continue;
    }
    note(`Skipping ${formatUpstream(candidate)}: injected project path ${targetProjectPath} is not open there`);
  }
  if (unknownCandidates.length > 0) {
    let fallback = unknownCandidates[0];
    return warn(`No ${ideLabel} upstream confirmed project path ${targetProjectPath}; using ${formatUpstream(fallback)} without verification`), fallback;
  }
  let fallback = candidates[0];
  return warn(`No ${ideLabel} upstream matched project path ${targetProjectPath}; using first reachable ${formatUpstream(fallback)}`), fallback;
}
async function closeUnusedUpstreams(candidates, selected) {
  await Promise.allSettled(candidates.filter((candidate) => candidate !== selected).map(async (candidate) => {
    try {
      await candidate.conn.client.close();
    } catch {}
  }));
}
async function ensureDiscovered() {
  if (ideaUpstream || riderUpstream)
    return;
  if (discoveryPromise)
    return discoveryPromise;
  return discoveryPromise = performDiscovery(), discoveryPromise;
}
async function performDiscovery() {
  try {
    let effectiveMcpUrl = explicitMcpUrlOverride ?? explicitMcpUrl;
    if (effectiveMcpUrl) {
      let conn = createUpstreamForUrl(effectiveMcpUrl);
      await conn.connect();
      let name = conn.client.getServerVersion()?.name ?? "";
      if (isRiderServerName(name))
        conn.updateProjectPath(path6.join(projectPath, RIDER_PROJECT_SUBPATH)), riderUpstream = conn;
      else
        ideaUpstream = conn;
      setupUpstreamClientHandlers(conn), updateProxyTooling();
      return;
    }
    let reachable = await findReachablePorts({
      preferredPorts,
      portScanStart,
      portScanLimit,
      scanTimeoutMs,
      buildUrl: buildStreamUrl,
      warn
    }), ideaCandidates = [], riderCandidates = [];
    for (let { url } of reachable) {
      let conn = createUpstreamForUrl(url);
      try {
        await conn.connect();
        let name = conn.client.getServerVersion()?.name ?? "", candidate = { conn, url, name };
        if (isRiderServerName(name))
          conn.updateProjectPath(path6.join(projectPath, RIDER_PROJECT_SUBPATH)), riderCandidates.push(candidate);
        else
          ideaCandidates.push(candidate);
      } catch (error) {
        let message = error instanceof Error ? error.message : String(error);
        warn(`Failed to connect to ${url}: ${message}`);
      }
    }
    let selectedIdea = await chooseUpstreamForProject(ideaCandidates, "IDEA", projectPath), selectedRider = await chooseUpstreamForProject(riderCandidates, "Rider", path6.join(projectPath, RIDER_PROJECT_SUBPATH));
    if (await closeUnusedUpstreams(ideaCandidates, selectedIdea), await closeUnusedUpstreams(riderCandidates, selectedRider), selectedIdea)
      ideaUpstream = selectedIdea.conn, setupUpstreamClientHandlers(selectedIdea.conn), note(`IDEA upstream: ${formatUpstream(selectedIdea)}`);
    if (selectedRider)
      riderUpstream = selectedRider.conn, setupUpstreamClientHandlers(selectedRider.conn), note(`Rider upstream: ${formatUpstream(selectedRider)}`);
    if (!ideaUpstream && !riderUpstream)
      throw Error(`No IDE found. Install the "MCP Server" plugin and ensure it is enabled. Probed ports: ${preferredPorts.join(", ")} + scan ${portScanStart}..${portScanStart + portScanLimit - 1}`);
    if (ideaUpstream && riderUpstream)
      note("Multi-IDE mode: routing between IDEA and Rider");
    updateProxyTooling();
  } finally {
    discoveryPromise = null;
  }
}
var serverInfo = { name: "ij-mcp-proxy", version: "1.0.0" }, serverCapabilities = {
  tools: { listChanged: !0 },
  resources: { subscribe: !0, listChanged: !0 },
  prompts: { listChanged: !0 },
  logging: {}
}, proxyServer = new Server(serverInfo, { capabilities: serverCapabilities });
proxyServer.setRequestHandler(InitializeRequestSchema, async (request) => {
  await activateDetectedContainerSession(), await performDiscovery();
  let requestedVersion = request.params.protocolVersion, protocolVersion = SUPPORTED_PROTOCOL_VERSIONS.includes(requestedVersion) ? requestedVersion : LATEST_PROTOCOL_VERSION, instructions = buildInstructions(), effectiveServerInfo = containerSession ? { name: `ij-mcp-proxy [container:${containerSession.sessionId}]`, version: "1.0.0" } : serverInfo;
  return {
    protocolVersion,
    capabilities: serverCapabilities,
    serverInfo: effectiveServerInfo,
    ...instructions && { instructions }
  };
});
proxyServer.setRequestHandler(ListToolsRequestSchema, async () => {
  await activateDetectedContainerSession(), await ensureDiscovered();
  let ideaTools = ideaUpstream ? await ideaUpstream.getTools() : [], riderTools = riderUpstream ? await riderUpstream.getTools() : [], allUpstreamTools = mergeToolLists(ideaTools, riderTools, /* @__PURE__ */ new Set);
  return {
    tools: mergeToolLists(proxyToolSpecs, allUpstreamTools, BASE_BLOCKED_TOOL_NAMES)
  };
});
proxyServer.setRequestHandler(CallToolRequestSchema, async (request) => {
  if (await activateDetectedContainerSession())
    await ensureDiscovered(), await proxyServer.sendToolListChanged();
  let toolName = typeof request.params?.name === "string" ? request.params.name : "", rawArgs = request.params?.arguments, args = rawArgs && typeof rawArgs === "object" ? { ...rawArgs } : {}, clientTimeoutMs;
  try {
    clientTimeoutMs = extractClientTimeoutMs(args);
  } catch (error) {
    return makeToolError(error instanceof Error ? error.message : String(error));
  }
  if (containerSession)
    note(`Tool call: ${toolName} [container:${containerSession.sessionId}, proxy:${proxyToolNames.has(toolName)}, hasUpstream:${!!ideaUpstream}]`);
  return await requestContext.run({ clientTimeoutMs }, async () => {
    if (!toolName)
      return makeToolError("Tool name is required");
    if (BASE_BLOCKED_TOOL_NAMES.has(toolName))
      return makeToolError(`Tool '${toolName}' is not exposed by ij-proxy.`);
    if (await ensureDiscovered(), proxyToolNames.has(toolName)) {
      if (ideaProxyToolCall && riderProxyToolCall) {
        if (isMergeTool(toolName))
          return await callMergedProxyTool(toolName, args);
        let ide = resolveIdeForPath(args, projectPath), proxyCall = ide === "rider" ? riderProxyToolCall : ideaProxyToolCall, rewrittenArgs = rewriteArgsForTarget(ide === "rider" ? "target-rider" : "target-idea", args);
        try {
          return makeToolOutput(await proxyCall(toolName, rewrittenArgs));
        } catch (error) {
          let message = error instanceof Error ? error.message : String(error);
          return makeToolError(message);
        }
      }
      let proxyCall = ideaProxyToolCall ?? riderProxyToolCall;
      if (proxyCall)
        try {
          return makeToolOutput(await proxyCall(toolName, args));
        } catch (error) {
          let message = error instanceof Error ? error.message : String(error);
          return makeToolError(message);
        }
    }
    if (ideaUpstream && riderUpstream) {
      let route = resolveRoute(toolName, args, projectPath);
      switch (route) {
        case "merge":
          return await callMergedPassthroughTool(toolName, args);
        case "split-merge":
          return await callSplitMergedTool(toolName, args);
        case "target-idea":
        case "target-rider": {
          let target = route === "target-rider" ? riderUpstream : ideaUpstream;
          try {
            return await target.callToolForClient(toolName, rewriteArgsForTarget(route, args));
          } catch (error) {
            let message = error instanceof Error ? error.message : String(error);
            return makeToolError(message);
          }
        }
        case "primary":
          break;
      }
    }
    try {
      if (toolName === "lint_files")
        return await callSingleLintFilesTool(args);
      if (toolName === "reformat_file")
        return await callSingleReformatFileTool(args);
      return await primaryUpstream().callToolForClient(toolName, args);
    } catch (error) {
      let message = error instanceof Error ? error.message : String(error);
      return makeToolError(message);
    }
  });
});
proxyServer.fallbackRequestHandler = async (request) => (await ensureDiscovered(), await primaryUpstream().forwardRequest(request.method, request.params));
proxyServer.fallbackNotificationHandler = async (notification) => {
  await ensureDiscovered(), await primaryUpstream().forwardNotification(notification);
};
var stdioTransport = new StdioServerTransport;
stdioTransport.onerror = (error) => {
  warn(`Stdio transport error: ${error.message}`);
};
proxyServer.connect(stdioTransport).catch((error) => {
  let message = error instanceof Error ? error.message : String(error);
  warn(`Failed to start stdio transport: ${message}`);
});
async function callMergedProxyTool(toolName, args) {
  let results = await Promise.allSettled([
    ideaProxyToolCall(toolName, { ...args }),
    riderProxyToolCall(toolName, { ...args })
  ]);
  return mergeSettledResults(results, "proxy", [void 0, riderItemTransformer]);
}
async function callMergedPassthroughTool(toolName, args) {
  let results = await Promise.allSettled([
    ideaUpstream.callToolForClient(toolName, { ...args }),
    riderUpstream.callToolForClient(toolName, { ...args })
  ]);
  return mergeSettledResults(results, "passthrough", [void 0, riderItemTransformer]);
}
async function callSplitMergedTool(toolName, args) {
  switch (toolName) {
    case "lint_files":
      return await callSplitMergedLintFiles(args);
    case "reformat_file":
      return await callSplitMergedReformatFile(args);
    default:
      return makeToolError(`Tool '${toolName}' is not configured for split-merge routing.`);
  }
}
function upstreamForSide(side) {
  return side === "idea" ? ideaUpstream : riderUpstream;
}
async function callNativeLintFiles(side, args) {
  let upstream = upstreamForSide(side);
  if (!upstream?.toolSupport.hasLintFiles)
    throw Error(`Tool 'lint_files' is not supported by the ${side === "idea" ? "IDEA" : "Rider"} upstream.`);
  return await upstream.callToolForClient("lint_files", { ...args });
}
async function callNativeReformatFile(side, args) {
  let upstream = upstreamForSide(side);
  if (!upstream?.toolSupport.hasReformatFile)
    throw Error(`Tool 'reformat_file' is not supported by the ${side === "idea" ? "IDEA" : "Rider"} upstream.`);
  return extractTextFromResult(await upstream.callToolForClient("reformat_file", { ...args })) ?? "ok";
}
async function callSingleLintFilesTool(args) {
  let normalizedArgs = normalizeLintFilesArgs(args), side = getSingleUpstreamSide("lint_files"), result = await callLintFilesForSide(side, normalizedArgs), items = side === "rider" ? riderItemTransformer(result.items) : result.items;
  return createLintFilesToolOutput(result.more === !0 ? { items, more: !0 } : { items });
}
async function callSplitMergedLintFiles(args) {
  let normalizedArgs = normalizeLintFilesArgs(args), normalizedFilePaths = normalizedArgs.files, splitArgs;
  try {
    splitArgs = splitPathListArgsByIde(normalizedArgs, projectPath);
  } catch (error) {
    let message = error instanceof Error ? error.message : String(error);
    return makeToolError(message);
  }
  let calls = [];
  if (splitArgs.ideaArgs)
    calls.push({ promise: callLintFilesForSide("idea", splitArgs.ideaArgs) });
  if (splitArgs.riderArgs)
    calls.push({ promise: callLintFilesForSide("rider", splitArgs.riderArgs), transformer: riderItemTransformer });
  let results = await Promise.allSettled(calls.map((call) => call.promise));
  for (let result of results)
    if (result.status === "rejected") {
      let message = result.reason instanceof Error ? result.reason.message : String(result.reason);
      return makeToolError(message);
    }
  let mergedItems = [], more = !1;
  for (let i = 0;i < results.length; i++) {
    let result = results[i];
    if (result.status !== "fulfilled")
      continue;
    mergedItems.push(...transformLintItems(result.value.items, calls[i].transformer)), more = more || result.value.more === !0;
  }
  let items = orderLintItems(normalizedFilePaths, mergedItems);
  return createLintFilesToolOutput(more ? { items, more: !0 } : { items });
}
async function callSingleReformatFileTool(args) {
  let side = getSingleUpstreamSide("reformat_file"), result = await callReformatFileForSide(side, args);
  return makeToolOutput(result);
}
async function callSplitMergedReformatFile(args) {
  let normalizedArgs = normalizeReformatFileArgs(args), splitArgs;
  try {
    splitArgs = splitPathListArgsByIde(normalizedArgs, projectPath);
  } catch (error) {
    let message = error instanceof Error ? error.message : String(error);
    return makeToolError(message);
  }
  let calls = [];
  if (splitArgs.ideaArgs)
    calls.push(callReformatFileForSide("idea", splitArgs.ideaArgs));
  if (splitArgs.riderArgs)
    calls.push(callReformatFileForSide("rider", splitArgs.riderArgs));
  let results = await Promise.allSettled(calls);
  for (let result of results)
    if (result.status === "rejected") {
      let message = result.reason instanceof Error ? result.reason.message : String(result.reason);
      return makeToolError(message);
    }
  return makeToolOutput("ok");
}
async function callReformatFileForSide(side, args) {
  let normalizedArgs = normalizeReformatFileArgs(args);
  return await callNativeReformatFile(side, normalizedArgs);
}
async function callLintFilesForSide(side, args) {
  let normalizedArgs = normalizeLintFilesArgs(args), result = parseLintFilesToolResult(await callNativeLintFiles(side, normalizedArgs)), filePaths = normalizedArgs.files, items = orderLintItems(filePaths, result.items);
  return result.more === !0 ? { items, more: !0 } : { items };
}
function getSingleUpstreamSide(toolName) {
  if (ideaUpstream)
    return "idea";
  if (riderUpstream)
    return "rider";
  throw Error(`Tool '${toolName}' is not available because no upstream is connected.`);
}
function normalizeLintFilesArgs(args) {
  if (Object.prototype.hasOwnProperty.call(args, "file_paths"))
    throw Error("file_paths is no longer supported; use files");
  let files = normalizeLintFilesArg(args.files), timeout = normalizeLintTimeoutArg(args.timeout), normalizedArgs = {
    ...args,
    files
  };
  if (timeout !== void 0)
    normalizedArgs.timeout = timeout;
  else
    delete normalizedArgs.timeout;
  return normalizedArgs;
}
function normalizeLintFilesArg(value) {
  if (!Array.isArray(value))
    throw Error("files must be an array of non-empty strings");
  let result = [], seen = /* @__PURE__ */ new Set;
  for (let rawPath of value) {
    if (typeof rawPath !== "string" || rawPath.trim().length === 0)
      throw Error("files must contain non-empty strings");
    let normalizedPath = rawPath.trim();
    if (seen.has(normalizedPath))
      continue;
    seen.add(normalizedPath), result.push(normalizedPath);
  }
  if (result.length === 0)
    throw Error("files must contain at least one path");
  return result;
}
function normalizeLintTimeoutArg(value) {
  if (value === void 0 || value === null)
    return;
  if (typeof value !== "number" || !Number.isInteger(value) || value < 0)
    throw Error("timeout must be a non-negative integer");
  return value;
}
function extractClientTimeoutMs(args) {
  let raw = args.timeout;
  if (raw === void 0 || raw === null)
    return;
  if (typeof raw !== "number" || !Number.isInteger(raw) || raw < 0)
    throw Error("timeout must be a non-negative integer (milliseconds)");
  return raw;
}
function parseLintFilesToolResult(result) {
  let structured = extractStructuredContent(result);
  if (!isRecord2(structured))
    throw Error("Upstream lint_files returned unexpected result");
  let items = extractItems({ structuredContent: structured });
  return structured.more === !0 ? { items, more: !0 } : { items };
}
function lintItemPathKey(filePath) {
  let normalized = normalizeProjectRelativePath(projectPath, filePath);
  return path6.sep === "\\" ? normalized.toLowerCase() : normalized;
}
function orderLintItems(filePaths, items) {
  let itemsByPath = /* @__PURE__ */ new Map;
  for (let item of items) {
    let key = lintItemPathKey(item.filePath);
    if (!itemsByPath.has(key))
      itemsByPath.set(key, item);
  }
  return filePaths.map((filePath) => itemsByPath.get(lintItemPathKey(filePath))).filter((item) => item != null);
}
function transformLintItems(items, transformer) {
  return transformer ? transformer(items) : items;
}
function createLintFilesToolOutput(result) {
  let structuredContent = result.more === !0 ? { items: result.items, more: !0 } : { items: result.items };
  return makeJsonToolOutput(structuredContent);
}
function logSettledErrors(results) {
  for (let r of results)
    if (r.status === "rejected")
      warn(`Merge: one upstream failed: ${r.reason instanceof Error ? r.reason.message : String(r.reason)}`);
}
function settledErrorOutput(results) {
  for (let r of results)
    if (r.status === "rejected") {
      let message = r.reason instanceof Error ? r.reason.message : String(r.reason);
      return makeToolError(message);
    }
  return makeToolError("All upstreams failed");
}
function extractItemsFromResult(value, mode) {
  let structured = extractStructuredContentFromResult(value, mode);
  if (!structured)
    return [];
  return extractItems({ structuredContent: structured });
}
function extractMoreFromResult(value, mode) {
  let structured = extractStructuredContentFromResult(value, mode);
  return isRecord2(structured) && structured.more === !0;
}
function extractStructuredContentFromResult(value, mode) {
  if (mode === "proxy")
    return extractStructuredContent(value);
  let text = extractTextFromResult(value);
  if (!text)
    return null;
  return extractStructuredContent({ content: [{ type: "text", text }] });
}
function isRecord2(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}
function mergeSettledResults(results, mode, transformers = []) {
  logSettledErrors(results);
  let allItems = [], more = !1, hasFulfilledResult = !1;
  for (let i = 0;i < results.length; i++) {
    let r = results[i];
    if (r.status !== "fulfilled")
      continue;
    hasFulfilledResult = !0;
    let value = r.value;
    if (value == null)
      continue;
    let items = extractItemsFromResult(value, mode), transformer = transformers[i];
    allItems.push(...transformer ? transformer(items) : items), more = more || extractMoreFromResult(value, mode);
  }
  if (hasFulfilledResult)
    return makeToolOutput(JSON.stringify(more ? { items: allItems, more: !0 } : { items: allItems }));
  return settledErrorOutput(results);
}
function makeToolOutput(text) {
  return {
    content: [
      {
        type: "text",
        text: String(text)
      }
    ]
  };
}
function makeJsonToolOutput(structuredContent) {
  return {
    content: [
      {
        type: "text",
        text: JSON.stringify(structuredContent)
      }
    ],
    structuredContent
  };
}
function makeToolError(text) {
  return {
    content: [
      {
        type: "text",
        text: String(text)
      }
    ],
    isError: !0
  };
}
function mergeToolLists(listA, listB, blockedNames) {
  let blocked = blockedNames instanceof Set ? blockedNames : new Set(blockedNames || []), result = [], seen = /* @__PURE__ */ new Set;
  for (let tool of listA || []) {
    if (!tool || typeof tool.name !== "string")
      continue;
    if (blocked.has(tool.name))
      continue;
    if (seen.has(tool.name))
      continue;
    seen.add(tool.name), result.push(tool);
  }
  if (Array.isArray(listB))
    for (let tool of listB) {
      let name = tool?.name;
      if (typeof name !== "string" || !name)
        continue;
      if (blocked.has(name))
        continue;
      if (seen.has(name))
        continue;
      seen.add(name), result.push(tool);
    }
  return result;
}
