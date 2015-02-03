var gulp = require('gulp')
var apidoc = require('gulp-apidoc')
var path = require('path')

var sources = path.normalize("../../platform/platform-impl/src")

gulp.task('apidoc', function () {
  apidoc.exec({src: sources, dest: (process.env.HOME || process.env.HOMEPATH || process.env.USERPROFILE) + "/idea-rest-api"})
})

gulp.task('default', ['apidoc'])

gulp.task('watch', function() {
  gulp.watch(sources + "/**/*.{clj,coffee,cs,dart,erl,go,java,js,php,py,rb,ts,pm}", ['apidoc'])
})