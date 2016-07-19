/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
const gulp = require("gulp")
const apidoc = require("gulp-apidoc")
const path = require("path")

const sources = path.resolve("../../platform/built-in-server/src")

gulp.task("apidoc", function (done) {
  var destDir = (process.env.HOME || process.env.HOMEPATH || process.env.USERPROFILE) + "/idea-rest-api";
  console.info("Generating docs from " + sources + " to " + destDir)
  apidoc({src: sources, dest: destDir}, done)
})

gulp.task('default', ["apidoc"])

gulp.task('watch', function() {
  gulp.watch(sources + "/**/*.{clj,coffee,cs,dart,erl,go,java,js,php,py,rb,ts,pm}", ['apidoc'])
})